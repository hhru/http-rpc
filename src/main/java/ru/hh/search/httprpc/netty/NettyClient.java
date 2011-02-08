package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
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
import org.jboss.netty.handler.codec.http.DefaultHttpRequest;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.BadResponseException;
import ru.hh.search.httprpc.Client;
import ru.hh.search.httprpc.ClientMethod;
import ru.hh.search.httprpc.Envelope;
import ru.hh.search.httprpc.HttpRpcNames;
import ru.hh.search.httprpc.Serializer;

public class NettyClient  extends AbstractService implements Client {
  public static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
  
  private final String basePath;
  private final ClientBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();

  public NettyClient(Map<String, Object> options, String basePath, int ioThreads) {
    this.basePath = basePath;
    ChannelFactory factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), ioThreads);
    bootstrap = new ClientBootstrap(factory);
    bootstrap.setOptions(options);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("codec", new HttpClientCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        return pipeline;
      }
    });
    start();
  }

  @Override
  protected void doStart() {
    notifyStarted();
  }

  @Override
  protected void doStop() {
    logger.debug("stopping");
    try {
      allChannels.close().awaitUninterruptibly();
      bootstrap.releaseExternalResources();
      logger.info("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("shutdown failed", e);
      throw e;
    }
  }

  @Override
  public <O, I> ClientMethod<O, I> createMethod(String path, Serializer<? super I> encoder, Serializer<? extends O> decoder) {
    return new NettyClientMethod<O, I>(basePath + path, encoder, decoder);
  }

  private class NettyClientMethod<O, I> implements ClientMethod<O, I> {
    private final String fullPath;
    private final Serializer<? super I> encoder;
    private final Serializer<? extends O> decoder;

    private NettyClientMethod(String fullPath, Serializer<? super I> encoder, Serializer<? extends O> decoder) {
      this.fullPath = fullPath;
      this.encoder = encoder;
      this.decoder = decoder;
    }

    @Override
    public ListenableFuture<O> call(final InetSocketAddress address, final Envelope envelope, final I input) {
      if (envelope == null) {
        throw new NullPointerException("envelope is null");
      }
      ChannelFuture connectFuture = bootstrap.connect(address);
      final ClientFuture<O> clientFuture = new ClientFuture<O>(connectFuture);
      final ClientHandler handler = new ClientHandler(clientFuture);
      connectFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture channelFuture) throws Exception {
          Channel channel = channelFuture.getChannel();
          allChannels.add(channel);
          if (channelFuture.isSuccess()) {
            if (!clientFuture.isCancelled()) {
              channel.getPipeline().addLast("handler", handler);
              QueryStringEncoder uriEncoder = new QueryStringEncoder(fullPath);
              uriEncoder.addParam(HttpRpcNames.TIMEOUT, Long.toString(envelope.timeoutMilliseconds));
              uriEncoder.addParam(HttpRpcNames.REQUEST_ID, envelope.requestId);
              HttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, uriEncoder.toString());
              byte[] bytes = encoder.toBytes(input);
              request.setHeader(HttpHeaders.Names.CONTENT_TYPE, encoder.getContentType());
              request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
              request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
              request.setContent(ChannelBuffers.wrappedBuffer(bytes));
              // TODO handle write failure
              channel.write(request);
            }
          } else {
            logger.error("connection failed", channelFuture.getCause());
            clientFuture.setException(channelFuture.getCause());
          }
        }
      });
      return clientFuture;
    }
  
    private class ClientHandler extends SimpleChannelUpstreamHandler {
      private final ClientFuture<O> future;
  
      public ClientHandler(ClientFuture<O> future) {
        this.future = future;
      }
  
      @Override
      public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpResponse response = (HttpResponse) e.getMessage();
        ChannelBuffer content = response.getContent();
        if (response.getStatus().getCode() == 200) {
          // TODO handle decoder exceptions
          // TODO see org.jboss.netty.handler.codec.protobuf.ProtobufDecoder.decode()
          O result = decoder.fromInputStream(new ChannelBufferInputStream(content));
          if (!future.set(result)) {
            logger.warn("server response returned too late, future has already been cancelled");
          }
        } else {
          StringBuilder message = new StringBuilder("server at ").append(e.getChannel().getRemoteAddress())
            .append(" returned: ").append(response.getStatus().toString());
          String contentType = response.getHeader(HttpHeaders.Names.CONTENT_TYPE);
          String details = null;
          if (contentType != null && contentType.contains("text/plain")) {
            details = content.toString(CharsetUtil.UTF_8);
          }
          logger.warn("{}, remote details:\n {}", message, details);
          if (!future.setException(new BadResponseException(message.toString(), details))) {
            logger.warn("bad server response returned too late, future has already been cancelled");
          }
        }
      }
    
      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent event) throws Exception {
        Throwable cause = event.getCause();
        if (future.isCancelled() && cause instanceof ClosedChannelException) {
          logger.debug("attempt to use closed channel after cancelling request", cause);
        } else {
          logger.error("client got exception, closing channel", cause);
          if (future.setException(cause)) {
            logger.warn("failed to set exception for future, cancelled: {}, done: {}", future.isCancelled(), future.isDone());
          }
          event.getChannel().close();
        }
      }
    }
  }
}
