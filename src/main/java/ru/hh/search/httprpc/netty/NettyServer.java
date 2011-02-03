package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
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
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.handler.execution.ExecutionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Envelope;
import ru.hh.search.httprpc.HttpRpcNames;
import ru.hh.search.httprpc.Serializer;
import ru.hh.search.httprpc.ServerMethod;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class NettyServer extends AbstractService {
  
  public static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
  
  private final ServerBootstrap bootstrap;
  private final ExecutionHandler executionHandler;
  private final ChannelGroup allChannels = new DefaultChannelGroup();
  private final ConcurrentMap<String, Descriptor> methods = new ConcurrentHashMap<String, Descriptor>();
  private final String basePath;
  
  /**
   * @param bootstrapOptions {@link org.jboss.netty.bootstrap.Bootstrap#setOptions(java.util.Map)}
   * @param ioThreads the maximum number of I/O worker threads for {@link org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory#NioServerSocketChannelFactory(java.util.concurrent.Executor, java.util.concurrent.Executor, int)}
   * @param callExecutor Executor to run ServerMethod.call()
   */
  public NettyServer(Map<String, Object> bootstrapOptions, String basePath, int ioThreads, Executor callExecutor) {
    ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), 
      ioThreads);
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(bootstrapOptions);
    executionHandler = new ExecutionHandler(callExecutor);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new HttpRequestDecoder(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
          new HttpResponseEncoder(),
          executionHandler,
          new RequestHandler());
      }
    });
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
      QueryStringDecoder uriDecoder = new QueryStringDecoder(request.getUri());
      // TODO: validate query parameters
      Envelope envelope = new Envelope(Integer.parseInt(uriDecoder.getParameters().get(HttpRpcNames.TIMEOUT).iterator().next()),
        uriDecoder.getParameters().get(HttpRpcNames.REQUEST_ID).iterator().next());
      // TODO: no method??
      Descriptor descriptor = methods.get(uriDecoder.getPath());
      @SuppressWarnings({"unchecked"}) 
      Object result = descriptor.method.call(envelope, 
        descriptor.decoder.fromInputStream(new ChannelBufferInputStream(request.getContent())));
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      byte[] bytes = descriptor.encoder.toBytes(result);
      response.setHeader(HttpHeaders.Names.CONTENT_TYPE, descriptor.encoder.getContentType());
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
      executionHandler.releaseExternalResources();
      logger.info("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("can't stop", e);
      notifyFailed(e);
      throw e;
    }
  }
  
  public void register(String path, ServerMethod method, Serializer encoder, Serializer decoder) {
    methods.put(basePath + path, new Descriptor(method, encoder, decoder));
  }
  
  private static class Descriptor {
    final ServerMethod method;
    final Serializer encoder;
    final Serializer decoder;

    private Descriptor(ServerMethod method, Serializer encoder, Serializer decoder) {
      this.method = method;
      this.encoder = encoder;
      this.decoder = decoder;
    }
  }
}
