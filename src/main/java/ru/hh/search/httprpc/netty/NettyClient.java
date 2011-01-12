package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ValueFuture;
import java.net.InetSocketAddress;
import java.net.URI;
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
import ru.hh.search.httprpc.Envelope;
import ru.hh.search.httprpc.Serializer;

public class NettyClient  extends AbstractService implements Client {
  public static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
  
  private final Serializer serializer;
  
  private final ClientBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();

  public NettyClient(Map<String, Object> options, Serializer serializer) {
    this.serializer = serializer;
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
  }

  @Override
  protected void doStart() {
    // TODO nothing to do, get rid of client.start()
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
  public <O, I> ClientMethod<O, I> createMethod(String path, Class<O> outputClass) {
    return new NettyClientMethod<O, I>(path, outputClass);
  }

  private class NettyClientMethod<O, I> implements ClientMethod<O, I> {
    final String path;
    final Class<O> outputClass;

    private NettyClientMethod(String path, Class<O> outputClass) {
      this.path = path;
      this.outputClass = outputClass;
    }

    @Override
    public ListenableFuture<O> call(final InetSocketAddress address, final Envelope envelope, final I input) {
      final ClientHandler<O> handler = new ClientHandler<O>(outputClass);
      ChannelFuture connectFuture = bootstrap.connect(address);
      connectFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            Channel channel = future.getChannel();
            allChannels.add(channel);
            channel.getPipeline().addLast("handler", handler);
            HttpRequest request = new DefaultHttpRequest(
                    HttpVersion.HTTP_1_1, HttpMethod.POST, new URI(path).toASCIIString());
            byte[] bytes = serializer.toBytes(input);
            request.setHeader(HttpHeaders.Names.CONTENT_TYPE, serializer.getContentType());
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
  
    private class ClientHandler<O> extends SimpleChannelUpstreamHandler {
      
      // TODO request cancellation
      private final ValueFuture<O> future = ValueFuture.create();
      private final Class<O> outputClass;
  
      public ClientHandler(Class<O> outputClass) {
        this.outputClass = outputClass;
      }
  
      @Override
      public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        HttpResponse response = (HttpResponse) e.getMessage();
        // TODO handle remote exceptions
        if (response.getStatus().getCode() == 200) {
          ChannelBuffer content = response.getContent();
          if (content.readable()) {
            future.set(serializer.fromInputStream(new ChannelBufferInputStream(content), outputClass));
          }
        }
      }
    
      @Override
      public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.error("client got exception, closing channel", e.getCause());
        e.getChannel().close();
      }
  
      public ListenableFuture<O> getFuture() {
        return future;
      }
    }
    
  }
}
