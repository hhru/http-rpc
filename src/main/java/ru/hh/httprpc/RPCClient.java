package ru.hh.httprpc;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.CharsetUtil;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.serialization.Serializer;
import ru.hh.httprpc.util.netty.Http;

public class RPCClient extends AbstractService {
  public static final Logger LOGGER = LoggerFactory.getLogger(RPCClient.class);

  private final Timer timer = new HashedWheelTimer(10, TimeUnit.MILLISECONDS);
  private final String basePath;
  private final ClientBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();
  private final Serializer serializer;
  private final boolean httpKeepAlive;

  public RPCClient(TcpOptions options, String basePath, int ioThreads, Serializer serializer) {
    this(options, basePath, ioThreads, serializer, false);
  }

  public RPCClient(TcpOptions options, String basePath, int ioThreads, Serializer serializer, boolean httpKeepAlive) {
    this.basePath = basePath;
    this.serializer = serializer;
    this.httpKeepAlive = httpKeepAlive;
    ChannelFactory factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), ioThreads);
    bootstrap = new ClientBootstrap(factory);
    bootstrap.setOptions(options.toMap());
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
            new HttpClientCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE)
        );
      }
    });
    startAsync();
  }

  @Override
  protected void doStart() {
    LOGGER.debug("started");
    notifyStarted();
  }

  @Override
  protected void doStop() {
    LOGGER.debug("stopping");
    try {
      allChannels.close().awaitUninterruptibly();
      bootstrap.releaseExternalResources();
      LOGGER.trace("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      LOGGER.error("shutdown failed", e);
      throw e;
    } finally {
      timer.stop();
    }
  }

  public <I, O> ClientMethod<I, O> createMethod(RPC<I, O> signature) {
    return new NettyClientMethod<>(
        basePath + signature.path,
        serializer.encoder(signature.inputClass),
        serializer.decoder(signature.outputClass)
    );
  }

  private class NettyClientMethod<I, O> implements ClientMethod<I, O> {
    private final String fullPath;
    Function<I, ChannelBuffer> encoder;
    Function<ChannelBuffer, O> decoder;

    private NettyClientMethod(String fullPath, Function<I, ChannelBuffer> encoder, Function<ChannelBuffer, O> decoder) {
      this.fullPath = fullPath;
      this.encoder = encoder;
      this.decoder = decoder;
    }

    @Override
    public ListenableFuture<O> call(final InetSocketAddress address, final Envelope envelope, final I input) {
      Preconditions.checkNotNull(envelope, "envelope");

      ChannelFuture connectFuture = bootstrap.connect(address);
      final Channel channel = connectFuture.getChannel();
      final ClientFuture<O> clientFuture = new ClientFuture<>(channel);
      allChannels.add(channel);

      connectFuture.addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            if (!httpKeepAlive) {
              channel.getPipeline().addFirst(ReadTimeoutHandler.class.getSimpleName(), new ReadTimeoutHandler(timer, envelope.timeoutMillis, TimeUnit.MILLISECONDS));
            }
            channel.getPipeline().addLast("handler", new ClientHandler(clientFuture));
            Http.request(
                HttpMethod.POST,
                Http.url(fullPath).
                    param(HttpRpcNames.TIMEOUT, envelope.timeoutMillis).
                    param(HttpRpcNames.REQUEST_ID, envelope.requestId) //TODO: remove REQUEST_ID get param from url
            ).
                host(address.getHostHttpHeaderValue()).
                header(HttpRpcNames.REQUEST_ID_HEADER_NAME, envelope.requestId).
                setKeepAlive(httpKeepAlive).
                containing(serializer.getContentType(), encoder.apply(input)).
                sendTo(channel);
          } else {
            LOGGER.debug("connection failed", future.getCause());
            clientFuture.setException(future.getCause());
          }
        }
      });
      return clientFuture;
    }
  
    private class ClientHandler extends SimpleChannelUpstreamHandler {
      private final ClientFuture<O> future;
  
      private ClientHandler(ClientFuture<O> future) {
        this.future = future;
      }
  
      @Override
      public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
        HttpResponse response = (HttpResponse) event.getMessage();
        ChannelBuffer content = response.getContent();
        if (response.getStatus().equals(HttpResponseStatus.OK)) {
          try {
            O result = decoder.apply(content);
            future.set(result);
          } catch (RuntimeException e) {
            LOGGER.debug("failed to decode response", e);
            future.setException(e);
          }
        } else {
          StringBuilder message = new StringBuilder("server at ").append(event.getChannel().getRemoteAddress()).append(fullPath)
            .append(" returned: ").append(response.getStatus().toString());
          final String contentType = response.headers().get(HttpHeaders.Names.CONTENT_TYPE);
          String details = null;
          if (contentType != null && contentType.contains("text/plain")) {
            details = content.toString(CharsetUtil.UTF_8);
          }
          LOGGER.debug("{}, remote details:\n {}", message, details);

          future.setException(new BadResponseException(message.toString(), details, response.getStatus()));
        }
      }

      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent event) throws Exception {
        Throwable cause = event.getCause();
        if (future.isCancelled() && cause instanceof ClosedChannelException) {
          LOGGER.debug("attempt to use closed channel after cancelling request", cause);
        } else {
          LOGGER.debug("client got exception, closing channel", cause);
          if (!future.setException(cause)) {
            LOGGER.warn(
                String.format("failed to set exception for future, cancelled: %b, done: %b", future.isCancelled(), future.isDone()),
                cause);
          }
          event.getChannel().close();
        }
      }
    }
  }
}
