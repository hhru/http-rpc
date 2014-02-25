package ru.hh.httprpc;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractService;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseEncoder;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.netty.handler.codec.http.HttpResponseStatus.SERVICE_UNAVAILABLE;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timer;
import io.netty.util.concurrent.ImmediateEventExecutor;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.netty.Http;

public class HTTPServer extends AbstractService {
  public static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);

  private static final int DEFAULT_NETWORK_TIMEOUT_MILLIS = 1000;

  private final EventLoopGroup workerGroup;
  private final ChannelTracker channelTracker = new ChannelTracker();
  private final Timer timer = new HashedWheelTimer();
  private final ServerBootstrap bootstrap;

  volatile private Channel serverChannel;

  public static class Builder {
    private TcpOptions options = TcpOptions.create();
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
  public HTTPServer(TcpOptions<TcpOptions> options, int ioThreads, final int concurrentRequestsLimit, final ChannelHandler handler) {
    this(builder().options(options).ioThreads(ioThreads).concurrentRequestsLimit(concurrentRequestsLimit).handler(handler));
  }

  public HTTPServer(final Builder builder) {
    workerGroup = new NioEventLoopGroup(builder.ioThreads);
    bootstrap = new ServerBootstrap()
        .group(workerGroup)
        .localAddress(builder.options.localAddress())
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) throws Exception {
            if (channelTracker.group.size() < builder.concurrentRequestsLimit) {
              RequestPhaseAwareIdleHandler idleEventHandler = new RequestPhaseAwareIdleHandler();
              ch.pipeline()
                  .addFirst("idleHandler", new IdleStateHandler(builder.readTimeout, builder.writeTimeout, 0, TimeUnit.MILLISECONDS))
                  .addLast(idleEventHandler)
                  .addLast("tracker", channelTracker)
                  .addLast(idleEventHandler.startWriting()) // after encoding http response (downstream) we're in a writing stage
                  .addLast("httpCodec", new HttpServerCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE))
                  .addLast("httpAggregator", new HttpObjectAggregator(Integer.MAX_VALUE))
                  .addLast(idleEventHandler.startProcessing()) // after encoding http response (downstream) we're in a writing stage
                      // todo: exception handler
                  .addLast("handler", builder.handler)
              ;
            } else {
              ch.pipeline()
                  .addLast(new SimpleChannelInboundHandler<Object>() {
                    @Override
                    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                      serviceUnavailable(ctx);
                    }

                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                      serviceUnavailable(ctx);
                    }

                    private void serviceUnavailable(ChannelHandlerContext ctx) {
                      Http.response(SERVICE_UNAVAILABLE)
                          .containing("httprpc configured to handle not more than " + builder.concurrentRequestsLimit + " concurrent requests")
                          .sendAndClose(ctx.channel());
                    }
                  })
                  .addLast(new HttpResponseEncoder());
            }
          }
        });
    builder.options.initializeBootstrap(bootstrap);
  }

  public InetSocketAddress getLocalAddress() {
    return InetSocketAddress.createFromJavaInetSocketAddress((java.net.InetSocketAddress) serverChannel.localAddress());
  }

  @Override
  protected void doStart() {
    logger.debug("starting");
    try {
      ChannelFuture bind = bootstrap.bind().sync();
      serverChannel = bind.channel();
      logger.debug("started");
      notifyStarted();
    } catch (RuntimeException e) {
      logger.error("can't start", e);
      notifyFailed(e);
      throw e;
    } catch (InterruptedException e) {
      logger.error("can't start", e);
      notifyFailed(e);
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void doStop() {
    logger.debug("stopping");
    try {
      serverChannel.close().awaitUninterruptibly();
      channelTracker.waitForChildren();
      timer.stop();
      workerGroup.shutdownGracefully();
      logger.debug("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("can't stop", e);
      notifyFailed(e);
      throw e;
    }
  }

  @ChannelHandler.Sharable
  private static class ChannelTracker extends ChannelInitializer {
    private final ChannelGroup group = new DefaultChannelGroup(ImmediateEventExecutor.INSTANCE);

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
      if (ClosedChannelException.class.isInstance(cause)) {
        logger.debug("Got " + cause.getClass().getName());
      } else {
        logger.error("Unexpected exception ", cause);
        Http.response(INTERNAL_SERVER_ERROR).
            containing(cause).
            sendAndClose(ctx.channel());
      }
    }

    @Override
    protected void initChannel(Channel ch) throws Exception {
      group.add(ch);
    }

    public void waitForChildren() {
      for (Channel channel : group) {
        channel.closeFuture().awaitUninterruptibly();
      }
    }
  }
}
