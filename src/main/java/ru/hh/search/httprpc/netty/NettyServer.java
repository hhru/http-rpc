package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Serializer;
import ru.hh.search.httprpc.ServerMethod;
import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class NettyServer extends AbstractService {
  
  public static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
  
  private final Serializer serializer;
  private final ServerBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();
  private final ConcurrentMap<String, ServerMethod> methods = new ConcurrentHashMap<String, ServerMethod>();
  private final String basePath;

  /**
   * @param options {@link org.jboss.netty.bootstrap.Bootstrap#setOptions(java.util.Map)}
   */
  public NettyServer(Map<String, Object> options, Serializer serializer, String basePath) {
    // TODO thread pool options
    ChannelFactory factory = new NioServerSocketChannelFactory(
      Executors.newCachedThreadPool(),
      Executors.newCachedThreadPool());
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(options);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        ChannelPipeline pipeline = pipeline();
        pipeline.addLast("decoder", new HttpRequestDecoder());
        // TODO get rid of chunks
        pipeline.addLast("aggregator", new HttpChunkAggregator(Integer.MAX_VALUE));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("handler", new RequestHandler());
        return pipeline;
      }
    });
    this.serializer = serializer;
    this.basePath = basePath;
  }
  
  private class RequestHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      allChannels.add(e.getChannel());
      ctx.sendUpstream(e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
      HttpRequest request = (HttpRequest) event.getMessage();
      // TODO: no method??
      // TODO: parse parameters to extract envelope, use path instead of whole uri
      ServerMethod method = methods.get(request.getUri());
      // TODO move outside IO thread pool
      @SuppressWarnings({"unchecked"}) 
      Object result = method.call(null, serializer.fromInputStream(new ChannelBufferInputStream(request.getContent()), 
        method.getInputClass()));
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      byte[] bytes = serializer.toBytes(result);
      response.setHeader(HttpHeaders.Names.CONTENT_TYPE, serializer.getContentType());
      request.setHeader(HttpHeaders.Names.CONTENT_LENGTH, bytes.length); 
      response.setContent(ChannelBuffers.wrappedBuffer(bytes));
      event.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
      logger.error("server got exception, closing channel", e.getCause());
      e.getChannel().close();
    }
  }

  @Override
  protected void doStart() {
    logger.debug("starting");
    try {
      Channel channel = bootstrap.bind();
      allChannels.add(channel);
      logger.info("started");
      notifyStarted();
    } catch (RuntimeException e){
      logger.error("can't start", e);
      notifyFailed(e);
      throw e;
    }
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
      logger.error("can't stop", e);
      notifyFailed(e);
      throw e;
    }
  }
  
  public void register(String path, ServerMethod method) {
    methods.put(basePath + path, method);
  }
}
