package ru.hh.httprpc;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractService;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;

import static java.util.concurrent.Executors.newCachedThreadPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelHandler;
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
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.netty.ChannelContextData;
import ru.hh.httprpc.util.netty.Http;

public class HTTPServer extends AbstractService {
  private static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
  private static final Logger requestsLogger = LoggerFactory.getLogger("requests");

  private static final int DEFAULT_NETWORK_TIMEOUT_MILLIS = 1000;

  private final ChannelTracker channelTracker = new ChannelTracker();
  private final Timer timer = new HashedWheelTimer();
  private final ServerBootstrap bootstrap;

  volatile private Channel serverChannel;

  public static class Builder {
    private TcpOptions options = new TcpOptions();
    private int ioThreads = 2;
    private int concurrentRequestsLimit = Runtime.getRuntime().availableProcessors();
    private int readTimeout = DEFAULT_NETWORK_TIMEOUT_MILLIS;
    private int writeTimeout = DEFAULT_NETWORK_TIMEOUT_MILLIS;
    private ChannelHandler handler;

    public Builder options(TcpOptions options) {
      this.options = options;
      return this;
    }

    public Builder ioThreads(int ioThreads) {
      this.ioThreads = ioThreads;
      return this;
    }

    public Builder concurrentRequestsLimit(int concurrentRequestsLimit) {
      this.concurrentRequestsLimit = concurrentRequestsLimit;
      return this;
    }

    /**
     * @param readTimeout in milliseconds
     */
    public Builder readTimeout(int readTimeout) {
      this.readTimeout = readTimeout;
      return this;
    }

    /**
     * @param writeTimeout in milliseconds
     */
    public Builder writeTimeout(int writeTimeout) {
      this.writeTimeout = writeTimeout;
      return this;
    }

    public Builder handler(ChannelHandler handler) {
      this.handler = handler;
      return this;
    }

    public HTTPServer build() {
      Preconditions.checkState(handler != null, "handler should be set");
      return new HTTPServer(this);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public HTTPServer(TcpOptions options, int ioThreads, final ChannelHandler handler) {
    this(options, ioThreads, Integer.MAX_VALUE, handler);
  }

  /**
   * @param ioThreads the maximum number of I/O worker threads
   * @param concurrentRequestsLimit the maximum number of open connections for the server
   * @deprecated use HTTPServer(Builder builder) and setters
   */
  public HTTPServer(TcpOptions options, int ioThreads, final int concurrentRequestsLimit, final ChannelHandler handler) {
    this(builder().options(options).ioThreads(ioThreads).concurrentRequestsLimit(concurrentRequestsLimit).handler(handler));
  }

  public HTTPServer(final Builder builder) {
    ChannelFactory factory = new NioServerSocketChannelFactory(newCachedThreadPool(), newCachedThreadPool(), builder.ioThreads);
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(builder.options.toMap());
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        if (channelTracker.group.size() < builder.concurrentRequestsLimit) {
          RequestPhaseAwareIdleHandler idleHandler = new RequestPhaseAwareIdleHandler();
          return Channels.pipeline(
              channelTracker,
              new IdleStateHandler(timer, builder.readTimeout, builder.writeTimeout, 0, TimeUnit.MILLISECONDS),
              idleHandler,
              idleHandler.startWriting(), // after encoding http response (downstream) we're in a writing stage
              new HttpServerCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
              idleHandler.startProcessing(), // after decoding http request (upstream) we're in a processing phase
              // todo: exception handler
              builder.handler
          );
        } else {
          return Channels.pipeline(
              channelTracker,
              new SimpleChannelUpstreamHandler() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                  ChannelContextData contextData = (ChannelContextData) e.getChannel().getAttachment();
                  Object message = e.getMessage();
                  if (message instanceof HttpRequest) {
                    contextData.setRequest((HttpRequest) message);
                  }
                  String errorMessage = "httprpc configured to handle not more than " + builder.concurrentRequestsLimit + " concurrent requests";
                  logger.error(errorMessage);
                  Http.response(SERVICE_UNAVAILABLE)
                      .containing(errorMessage)
                      .sendAndClose(e.getChannel());
                }
              },
              new HttpResponseEncoder()
          );
        }
      }
    });
  }

  public InetSocketAddress getLocalAddress() {
    return InetSocketAddress.createFromJavaInetSocketAddress((java.net.InetSocketAddress) serverChannel.getLocalAddress());
  }

  @Override
  protected void doStart() {
    logger.debug("starting");
    try {
      serverChannel = bootstrap.bind();
      logger.debug("started");
      notifyStarted();
    } catch (RuntimeException e) {
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
      channelTracker.waitForChildren();
      timer.stop();
      bootstrap.releaseExternalResources();
      logger.debug("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("can't stop", e);
      notifyFailed(e);
      throw e;
    }
  }

  @ChannelHandler.Sharable
  private static class ChannelTracker extends SimpleChannelUpstreamHandler {
    private final ChannelGroup group = new DefaultChannelGroup();

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent event) throws Exception {
      Throwable cause = event.getCause();
      Throwable rootCause = Throwables.getRootCause(cause);
      if (ClosedChannelException.class.isInstance(cause)) {
        logger.debug("Got " + cause.getClass().getName());
      } else if (IOException.class.isInstance(cause)) {
        logger.error("Unexpected IOexception  ", cause);
        event.getChannel().close();
      } else if (rootCause instanceof TimeoutException) {
        Http.response(SERVICE_UNAVAILABLE)
            .containing(rootCause)
            .sendAndClose(event.getChannel());
      } else {
        logger.error("Unexpected exception ", cause);
        Http.response(INTERNAL_SERVER_ERROR)
            .containing(cause)
            .sendAndClose(event.getChannel());
      }
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      Channel channel = e.getChannel();
      channel.setAttachment(new ChannelContextData(channel));
      group.add(e.getChannel());
      super.channelOpen(ctx, e);
    } // Todo: better handling for channels opened after calling waitUntilClosed()

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      log((ChannelContextData) e.getChannel().getAttachment());
      super.channelClosed(ctx, e);
    }

    public void waitForChildren() {
      for (Channel channel : group)
        channel.getCloseFuture().awaitUninterruptibly();
    }

    private void log(ChannelContextData contextData) {
      HttpRequest request = contextData.getRequest();
      HttpResponse response = contextData.getResponse();
      int statusCode = response == null ? 0 : response.getStatus().getCode();
      final String message;
      if (request == null) {
        message = String.format("%s %s %s %3d ms",
            contextData.getRemoteAddress(),
            "noRequestId",
            statusCode,
            contextData.getLifetime()
        );
      } else {
        String requestId = request.headers().get(HttpRpcNames.REQUEST_ID_HEADER_NAME);
        message = String.format("%s %s %s %3d ms %s %s %s",
            contextData.getRemoteAddress(),
            requestId == null ? "noRequestId" : requestId,
            statusCode,
            contextData.getLifetime(),
            request.getProtocolVersion(),
            request.getMethod(),
            request.getUri()
        );
      }
      requestsLogger.info(message);
    }
  }
}
