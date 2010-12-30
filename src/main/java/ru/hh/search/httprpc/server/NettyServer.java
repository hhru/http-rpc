package ru.hh.search.httprpc.server;

import com.google.common.util.concurrent.AbstractService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunkAggregator;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.ServerMethod;
import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class NettyServer extends AbstractService {
  
  public static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
  
  private final ServerBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();
  private final ConcurrentMap<String, ServerMethod> methods = new ConcurrentHashMap<String, ServerMethod>();

  /**
   * @param options {@link org.jboss.netty.bootstrap.Bootstrap#setOptions(java.util.Map)}
   */
  public NettyServer(Map<String, Object> options) {
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
        pipeline.addLast("aggregator", new HttpChunkAggregator(Integer.MAX_VALUE));
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("handler", new RequestHandler());
        return pipeline;
      }
    });
  }
  
  private class RequestHandler extends SimpleChannelUpstreamHandler {

    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      allChannels.add(e.getChannel());
      ctx.sendUpstream(e);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      HttpRequest request = (HttpRequest) e.getMessage();
      // TODO: no method??
      // TODO: parse parameters
      ServerMethod method = methods.get(request.getUri());
      // TODO: unserialize request
      // TODO: method types
      String result = ((ServerMethod<String, String>)method).call(null, request.getContent().toString(CharsetUtil.UTF_8));
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      response.setContent(ChannelBuffers.copiedBuffer(result, CharsetUtil.UTF_8));
      response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");
      e.getChannel().write(response).addListener(ChannelFutureListener.CLOSE);
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
  
  public void register(ServerMethod method) {
    methods.put(method.getPath(), method);
  }
}
