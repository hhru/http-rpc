package ru.hh.search.httprpc.client;

import com.google.common.util.concurrent.AbstractService;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.Client;

public class NettyClient  extends AbstractService implements Client {
  public static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
  
  private final ChannelFactory factory;
  private final ClientBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();

  public NettyClient(Map<String, Object> options) {
    factory = new NioClientSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool());
    bootstrap = new ClientBootstrap(factory);
    bootstrap.setOptions(options);
  }

  @Override
  protected void doStart() {
    logger.debug("starting");
    try {
      // TODO: do we need to connect here? http keepalive?
      bootstrap.connect().addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          if (future.isSuccess()) {
            allChannels.add(future.getChannel());
            logger.info("started");
            notifyStarted();
          } else {
            logger.error("connection failed");
            notifyFailed(future.getCause());
          }
        }
      });
    } catch (RuntimeException e) {
      logger.error("startup failed", e);
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
      logger.error("shutdown failed", e);
      throw e;
    }
  }

  public <R, A> Future<R> call(String methodName, Map<String, String> envelope, A argument) {
    throw new UnsupportedOperationException("not implemented");
  }
}
