package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import java.net.URI;
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
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpClientCodec;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Client;
import ru.hh.search.httprpc.ClientMethod;
import ru.hh.search.httprpc.Decoder;
import ru.hh.search.httprpc.Encoder;
import ru.hh.search.httprpc.Envelope;

public class NettyClient  extends AbstractService implements Client {
  public static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
  
  private final String basePath;
  private final ClientBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();

  public NettyClient(Map<String, Object> options, String basePath) {
    this.basePath = basePath;
    // TODO thread pool settings
    ChannelFactory factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    bootstrap = new ClientBootstrap(factory);
    bootstrap.setOptions(options);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("codec", new HttpClientCodec());
        // TODO get rid of chunks
        pipeline.addLast("aggregator", new HttpChunkAggregator(Integer.MAX_VALUE));
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
  public <O, I> ClientMethod<O, I> createMethod(String path, Encoder<? super I> encoder, Decoder<? extends O> decoder) {
    return new NettyClientMethod<O, I>(basePath + path, encoder, decoder);
  }

  private class NettyClientMethod<O, I> implements ClientMethod<O, I> {
    private final String fullPath;
    private final Encoder<? super I> encoder;
    private final Decoder<? extends O> decoder;

    private NettyClientMethod(String fullPath, Encoder<? super I> encoder, Decoder<? extends O> decoder) {
      this.fullPath = fullPath;
      this.encoder = encoder;
      this.decoder = decoder;
    }

    @Override
    public ListenableFuture<O> call(final InetSocketAddress address, final Envelope envelope, final I input) {
      ChannelFuture connectFuture = bootstrap.connect(address);
      final ClientHandler handler = new ClientHandler(connectFuture.getChannel());
      connectFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            Channel channel = future.getChannel();
            allChannels.add(channel);
            channel.getPipeline().addLast("handler", handler);
            HttpRequest request = new DefaultHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.POST, new URI(fullPath).toASCIIString());
            byte[] bytes = encoder.toBytes(input);
            request.setHeader(HttpHeaders.Names.CONTENT_TYPE, encoder.getContentType());
            request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, bytes.length); 
            request.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE);
            request.setContent(ChannelBuffers.wrappedBuffer(bytes));
            // TODO: use envelope
            // TODO handle write failure
            channel.write(request);
          } else {
            logger.error("connection failed", future.getCause());
            future.setFailure(future.getCause());
          }
        }
      });
      return handler.getFuture();
    }
  
    private class ClientHandler extends SimpleChannelUpstreamHandler {
      
      private final ClientFuture<O> future;
  
      public ClientHandler(Channel channel) {
        this.future = new ClientFuture<O>(channel);
      }
  
      @Override
      public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpResponse response = (HttpResponse) e.getMessage();
        // TODO handle remote exceptions
        if (response.getStatus().getCode() == 200) {
          ChannelBuffer content = response.getContent();
          // TODO see org.jboss.netty.handler.codec.protobuf.ProtobufDecoder.decode()
          if (!future.set(decoder.fromInputStream(new ChannelBufferInputStream(content)))) {
            logger.warn("server responce returned too late, future has been already cancelled");
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
      
      // TODO: handle channelDisconnected??

      public ListenableFuture<O> getFuture() {
        return future;
      }
    }
  }
}
