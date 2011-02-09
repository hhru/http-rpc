package ru.hh.search.httprpc.netty;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
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
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.jboss.netty.handler.codec.http.QueryStringDecoder;
import org.jboss.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Envelope;
import ru.hh.search.httprpc.HttpRpcNames;
import ru.hh.search.httprpc.Serializer;
import ru.hh.search.httprpc.ServerMethod;

public class NettyServer extends AbstractService {
  
  public static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
  
  private final ServerBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();
  private final ConcurrentMap<String, Descriptor> methods = new ConcurrentHashMap<String, Descriptor>();
  private final String basePath;
  private final ExecutorService methodCallbackExecutor = MoreExecutors.sameThreadExecutor();
  volatile private Channel serverChannel;
  
  /**
   * @param bootstrapOptions {@link org.jboss.netty.bootstrap.Bootstrap#setOptions(java.util.Map)}
   * @param ioThreads the maximum number of I/O worker threads for {@link org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory#NioServerSocketChannelFactory(java.util.concurrent.Executor, java.util.concurrent.Executor, int)}
   */
  public NettyServer(Map<String, Object> bootstrapOptions, String basePath, int ioThreads) {
    ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), 
      ioThreads);
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(bootstrapOptions);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new HttpRequestDecoder(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
          new HttpResponseEncoder(),
          new MethodCallHandler());
      }
    });
    this.basePath = basePath;
  }
  
  public InetSocketAddress getLocalAddress() {
    return (InetSocketAddress) serverChannel.getLocalAddress();
  }

  private class MethodCallHandler extends SimpleChannelUpstreamHandler {
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      allChannels.add(e.getChannel());
      ctx.sendUpstream(e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
      logger.error("server got an exception, closing channel", e.getCause());
      e.getChannel().close();
    }
    
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent event) throws Exception {
      HttpRequest request = (HttpRequest) event.getMessage();
      QueryStringDecoder uriDecoder = new QueryStringDecoder(request.getUri());
      // TODO: validate query parameters
      Envelope envelope = new Envelope(Integer.parseInt(uriDecoder.getParameters().get(HttpRpcNames.TIMEOUT).iterator().next()),
        uriDecoder.getParameters().get(HttpRpcNames.REQUEST_ID).iterator().next());
      // TODO: no method??
      final String path = uriDecoder.getPath();
      final Descriptor descriptor = methods.get(path);
      Object argument;
      final Channel channel = event.getChannel();
      try {
        // TODO see org.jboss.netty.handler.codec.protobuf.ProtobufDecoder.decode()
        argument = descriptor.decoder.fromInputStream(new ChannelBufferInputStream(request.getContent()));
      } catch (Exception decoderException) {
        channel.write(responseFromException(decoderException, HttpResponseStatus.BAD_REQUEST))
          .addListener(ChannelFutureListener.CLOSE);
        return;
      }
      try {
        @SuppressWarnings({"unchecked"}) 
        final ListenableFuture callFuture = descriptor.method.call(envelope, argument);
        Runnable onCallComplete = new Runnable() {
          @Override
          public void run() {
            try {
              HttpResponse response;
              try {
                Object result = callFuture.get();
                @SuppressWarnings({"unchecked"})
                byte[] bytes = descriptor.encoder.toBytes(result);
                response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                response.setHeader(HttpHeaders.Names.CONTENT_TYPE, descriptor.encoder.getContentType());
                response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, bytes.length);
                response.setContent(ChannelBuffers.wrappedBuffer(bytes));
              } catch (ExecutionException futureException) {
                logger.error(String.format("method on %s threw an exception", path), futureException.getCause());
                response = responseFromException(futureException.getCause(), HttpResponseStatus.INTERNAL_SERVER_ERROR);
              }
              if (channel.isOpen()) {
                channel.write(response).addListener(ChannelFutureListener.CLOSE);
              } else {
                logger.warn("client on {} had closed connection before method on '{}' finished", channel.getRemoteAddress(), path);
              }
            } catch (CancellationException e) {
              // nothing to do, channel hasbeen closed already 
            } catch (InterruptedException e) {
              logger.error("got impossible exception, closing channel", e);
              channel.close();
            }
          }
        };
        callFuture.addListener(onCallComplete, methodCallbackExecutor);
        channel.getCloseFuture().addListener(new ChannelFutureListener() {
          @Override
          public void operationComplete(ChannelFuture future) throws Exception {
            if (callFuture.cancel(true)) {
              logger.warn("method call on {} cancelled by closed client {} channel", path, channel.getRemoteAddress());
            }
          }
        });
      } catch (Exception callException) {
        channel.write(responseFromException(callException, HttpResponseStatus.INTERNAL_SERVER_ERROR))
          .addListener(ChannelFutureListener.CLOSE);
      }
    }

    private HttpResponse responseFromException(Throwable callException, HttpResponseStatus responseStatus) {
      HttpResponse response;
      response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, responseStatus);
      response.setContent(ChannelBuffers.copiedBuffer(Throwables.getStackTraceAsString(callException), CharsetUtil.UTF_8));
      response.setHeader(HttpHeaders.Names.CONTENT_TYPE, "text/plain; charset=UTF-8");
      response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, response.getContent().readableBytes());
      return response;
    }
  }
  
  @Override
  protected void doStart() {
    logger.debug("starting");
    try {
      serverChannel = bootstrap.bind();
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
      serverChannel.close().awaitUninterruptibly();
      for (Channel channel : allChannels) {
        channel.getCloseFuture().awaitUninterruptibly();
      }
      methodCallbackExecutor.shutdown();
      bootstrap.releaseExternalResources();
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
