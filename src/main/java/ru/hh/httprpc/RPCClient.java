package ru.hh.httprpc;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.serialization.Serializer;
import ru.hh.httprpc.util.netty.Http;

public class RPCClient extends AbstractService {
  public static final Logger logger = LoggerFactory.getLogger(RPCClient.class);

  private final String basePath;
  private final Bootstrap bootstrap;
  private final EventLoopGroup workerGroup;
  private final ChannelGroup allChannels;
  private final Serializer serializer;

  public RPCClient(TcpOptions<TcpOptions> options, String basePath, int ioThreads, Serializer serializer) {
    this.basePath = basePath;
    this.serializer = serializer;
    workerGroup = new NioEventLoopGroup(ioThreads);
    allChannels = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);
    bootstrap = new Bootstrap()
        .group(workerGroup)
        .localAddress(options.localAddress())
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<NioSocketChannel>() {
          @Override
          protected void initChannel(NioSocketChannel ch) throws Exception {
            ch.pipeline()
                .addLast(new HttpClientCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE))
                .addLast("httpAggregator", new HttpObjectAggregator(Integer.MAX_VALUE));
          }
        });
    options.initializeBootstrap(bootstrap);
    startAsync().awaitRunning();
  }

  @Override
  protected void doStart() {
    logger.debug("started");
    notifyStarted();
  }

  @Override
  protected void doStop() {
    logger.debug("stopping");
    try {
      allChannels.close().awaitUninterruptibly();
      workerGroup.shutdownGracefully();
      logger.trace("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("shutdown failed", e);
      throw e;
    }
  }

  public <I, O> ClientMethod<I, O> createMethod(RPC<I, O> signature) {
    return new NettyClientMethod<I, O>(
        basePath + signature.path,
        serializer.encoder(signature.inputClass),
        serializer.decoder(signature.outputClass)
    );
  }

  private class NettyClientMethod<I, O> implements ClientMethod<I, O> {
    private final String fullPath;
    Function<I, ByteBuf> encoder;
    Function<ByteBuf, O> decoder;

    private NettyClientMethod(String fullPath, Function<I, ByteBuf> encoder, Function<ByteBuf, O> decoder) {
      this.fullPath = fullPath;
      this.encoder = encoder;
      this.decoder = decoder;
    }

    @Override
    public ListenableFuture<O> call(final InetSocketAddress address, final Envelope envelope, final I input) {
      Preconditions.checkNotNull(envelope, "envelope");

      ChannelFuture connectFuture = bootstrap.connect(address);
      final Channel channel = connectFuture.channel();
      final ClientFuture<O> clientFuture = new ClientFuture<O>(channel);
      allChannels.add(channel);

      connectFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            channel.pipeline()
                .addFirst(ReadTimeoutHandler.class.getSimpleName(), new ReadTimeoutHandler(envelope.timeoutMillis, TimeUnit.MILLISECONDS))
                .addLast("handler", new ClientHandler(clientFuture));
            Http.request(
                HttpMethod.POST,
                Http.url(fullPath).
                    param(HttpRpcNames.TIMEOUT, envelope.timeoutMillis).
                    param(HttpRpcNames.REQUEST_ID, envelope.requestId)
            ).
                host(address.getHostHttpHeaderValue()).
                containing(serializer.getContentType(), encoder.apply(input)).
                sendTo(channel);
          } else {
            logger.debug("connection failed", future.cause());
            clientFuture.setException(future.cause());
          }
        }
      });
      return clientFuture;
    }

    private class ClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
      private final ClientFuture<O> future;

      public ClientHandler(ClientFuture<O> future) {
        this.future = future;
      }

      @Override
      protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
        ByteBuf content = response.content();
        if (response.getStatus().equals(HttpResponseStatus.OK)) {
          try {
            O result = decoder.apply(content);
            future.set(result);
          } catch (RuntimeException e) {
            logger.debug("failed to decode response", e);
            future.setException(e);
          }
        } else {
          StringBuilder message = new StringBuilder("server at ").append(ctx.channel().remoteAddress()).append(fullPath)
              .append(" returned: ").append(response.getStatus().toString());
          String contentType = response.headers().get(HttpHeaders.Names.CONTENT_TYPE);
          String details = null;
          if (contentType != null && contentType.contains("text/plain")) {
            details = content.toString(CharsetUtil.UTF_8);
          }
          logger.debug("{}, remote details:\n {}", message, details);

          future.setException(new BadResponseException(message.toString(), details));
        }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (future.isCancelled() && cause instanceof ClosedChannelException) {
          logger.debug("attempt to use closed channel after cancelling request", cause);
        } else {
          logger.debug("client got exception, closing channel", cause);
          if (!future.setException(cause)) {
            logger.warn(
                String.format("failed to set exception for future, cancelled: %b, done: %b", future.isCancelled(), future.isDone()),
                cause);
          }
          ctx.close();
        }
      }
    }
  }
}
