package ru.hh.search.httprpc.client;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ValueFuture;
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
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Client;
import ru.hh.search.httprpc.Serializer;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;

public class NettyClient  extends AbstractService implements Client {
  public static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
  
  private final Serializer serializer;
  
  private final ClientBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();

  public NettyClient(Map<String, Object> options, Serializer serializer) {
    this.serializer = serializer;
    ChannelFactory factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    bootstrap = new ClientBootstrap(factory);
    bootstrap.setOptions(options);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      @Override
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("codec", new HttpClientCodec());
        pipeline.addLast("aggregator", new HttpChunkAggregator(Integer.MAX_VALUE));
        return pipeline;
      }
    });
  }

  @Override
  protected void doStart() {
    // nothing to do
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

  public <O, I> ListenableFuture<O> call(final String path, final Map<String, String> envelope, final I input, 
                                         final Class<O> outputClass) {
    final ClientHandler<O> handler = new ClientHandler<O>(outputClass);
    ChannelFuture connectFuture = bootstrap.connect();
    connectFuture.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
          Channel channel = future.getChannel();
          allChannels.add(channel);
          channel.getPipeline().addLast("handler", handler);
          HttpRequest request = new DefaultHttpRequest(
                  HttpVersion.HTTP_1_1, HttpMethod.POST, new URI(path).toASCIIString());
          request.setContent(ChannelBuffers.wrappedBuffer(serializer.toBytes(input)));
          request.setHeader(CONTENT_TYPE, serializer.getContentType());
          // TODO content-length header
          // TODO "accept" header
          // TODO: use envelope
          channel.write(request);
        } else {
          logger.error("connection failed", future.getCause());
        }
      }
    });
    return handler.getFuture();
  }

  private class ClientHandler<O> extends SimpleChannelUpstreamHandler {
    
    private final ValueFuture<O> future = ValueFuture.create();
    private final Class<O> outputClass;

    public ClientHandler(Class<O> outputClass) {
      this.outputClass = outputClass;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      HttpResponse response = (HttpResponse) e.getMessage();
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
