package ru.hh.httprpc;

import com.google.common.util.concurrent.AbstractService;
import java.nio.channels.ClosedChannelException;

import java.util.concurrent.Executors;
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
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.util.netty.Http;

public class HTTPServer extends AbstractService {
  public static final Logger logger = LoggerFactory.getLogger(HTTPServer.class);
  
  private final ServerBootstrap bootstrap;
  private final ChannelTracker channelTracker = new ChannelTracker();
  volatile private Channel serverChannel;

  public HTTPServer(TcpOptions options, int ioThreads, final ChannelHandler handler) {
    this(options, ioThreads, Integer.MAX_VALUE, handler);
  }

  /**
   * @param ioThreads the maximum number of I/O worker threads
   * @param concurrentRequestsLimit the maximum number of open connections for the server
   */
  public HTTPServer(TcpOptions options, int ioThreads, final int concurrentRequestsLimit, final ChannelHandler handler) {
    ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), ioThreads);
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(options.toMap());
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        if (channelTracker.group.size() < concurrentRequestsLimit) {
          return Channels.pipeline(
              channelTracker,
              new HttpServerCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
              // todo: exception handler
              handler
          );
        } else {
          return Channels.pipeline(
              new SimpleChannelUpstreamHandler() {
                @Override
                public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
                  Http.response(Http.TOO_MANY_REQUESTS)
                      .containing("httprpc configured to handle not more than " + concurrentRequestsLimit + " concurrent requests")
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

  protected void doStart() {
    logger.debug("starting");
    try {
      serverChannel = bootstrap.bind();
      logger.debug("started");
      notifyStarted();
    } catch (RuntimeException e){
      logger.error("can't start", e);
      notifyFailed(e);
      throw e;
    }
  }

  protected void doStop() {
    logger.debug("stopping");
    try {
      serverChannel.close().awaitUninterruptibly();
      channelTracker.waitForChildren();
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

    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
      if (ClosedChannelException.class.isInstance(e.getCause())) {
        logger.debug("Got " + e.getCause().getClass().getName());
      } else {
        logger.error("Unexpected exception ", e.getCause());
        Http.response(INTERNAL_SERVER_ERROR).
            containing(e.getCause()).
            sendAndClose(e.getChannel());
      }
    }

    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      group.add(e.getChannel());
      ctx.sendUpstream(e);
    } // Todo: better handling for channels opened after calling waitUntilClosed()

    public void waitForChildren() {
      for (Channel channel : group)
        channel.getCloseFuture().awaitUninterruptibly();
    }
  }
}
