package ru.hh.search.httprpc.server;

import com.google.common.util.concurrent.AbstractService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.ServerMethod;

public class NettyServer extends AbstractService {
  
  public static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
  
  private final ServerBootstrap bootstrap;
  private final ChannelFactory factory;
  private final ChannelGroup allChannels = new DefaultChannelGroup();
  private final ConcurrentMap<String, ServerMethod> methods = new ConcurrentHashMap<String, ServerMethod>();

  /**
   * @param options {@link org.jboss.netty.bootstrap.Bootstrap#setOptions(java.util.Map)}
   */
  public NettyServer(Map<String, Object> options) {
    // TODO thread pool options
    factory = new NioServerSocketChannelFactory(
                        Executors.newCachedThreadPool(),
                        Executors.newCachedThreadPool());
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(options);
    // TODO pipeline factory
  }

  @Override
  protected void doStart() {
    logger.debug("starting");
    try {
      Channel channel = bootstrap.bind();
      allChannels.add(channel); // TODO add clients' channels
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
      factory.releaseExternalResources();
      logger.info("stopped");
      notifyStopped();
    } catch (RuntimeException e) {
      logger.error("can't stop", e);
      notifyFailed(e);
      throw e;
    }
  }
  
  public void register(ServerMethod method) {
    methods.put(method.getName(), method);
  }
}
