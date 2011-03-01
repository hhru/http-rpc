package ru.hh.httprpc;

import com.google.common.util.concurrent.AbstractService;
import java.net.InetSocketAddress;
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
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.httprpc.serialization.Serializer;

public class RPCServer extends AbstractService {
  public static final Logger logger = LoggerFactory.getLogger(RPCServer.class);
  
  private final ServerBootstrap bootstrap;
  private final ChildChannelTracker childChannelTracker = new ChildChannelTracker();
  volatile private Channel serverChannel;
  
  /**
   * @param options
   * @param ioThreads the maximum number of I/O worker threads for {@link org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory#NioServerSocketChannelFactory(java.util.concurrent.Executor, java.util.concurrent.Executor, int)}
   * @param serializer
   */
  public RPCServer(TcpOptions options, int ioThreads) {
    ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), ioThreads);
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(options.toMap());
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
            childChannelTracker,
            new HttpServerCodec(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
            new ServerMethodCallHandler(methods)
        );
      }
    });
  }
  
  public InetSocketAddress getLocalAddress() {
    return (InetSocketAddress) serverChannel.getLocalAddress();
  }

  protected void doStart() {
    logger.trace("starting");
    try {
      serverChannel = bootstrap.bind();
      logger.trace("started");
      notifyStarted();
    } catch (RuntimeException e){
      logger.error("can't start", e);
      notifyFailed(e);
      throw e;
    }
  }

  protected void doStop() {
    logger.trace("stopping");
    try {
      serverChannel.close().awaitUninterruptibly();
      childChannelTracker.waitUntilClosed();
      bootstrap.releaseExternalResources();
      logger.trace("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("can't stop", e);
      notifyFailed(e);
      throw e;
    }
  }
  
  @ChannelHandler.Sharable
  private static class ChildChannelTracker extends SimpleChannelUpstreamHandler {
    private final ChannelGroup group = new DefaultChannelGroup();

    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
      group.add(e.getChannel());
      ctx.sendUpstream(e);
    }

    public void waitUntilClosed() {
      for (Channel channel : group)
        channel.getCloseFuture().awaitUninterruptibly();
    }
  }
}
