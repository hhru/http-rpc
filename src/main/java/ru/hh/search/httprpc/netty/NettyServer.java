package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.MoreExecutors;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.hh.search.httprpc.RPC;
import ru.hh.search.httprpc.SerializerFactory;
import ru.hh.search.httprpc.ServerMethod;

public class NettyServer extends AbstractService {
  
  public static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
  
  private final ServerBootstrap bootstrap;
  private final ChannelGroup allChannels = new DefaultChannelGroup();
  private final ConcurrentMap<String, ServerMethodDescriptor> methods = new ConcurrentHashMap<String, ServerMethodDescriptor>();
  private final String basePath;
  private final ExecutorService methodCallbackExecutor = MoreExecutors.sameThreadExecutor();
  private final SerializerFactory serializerFactory;
  volatile private Channel serverChannel;
  
  /**
   * @param bootstrapOptions {@link org.jboss.netty.bootstrap.Bootstrap#setOptions(java.util.Map)} TODO replace by TcpOptions struct
   * @param ioThreads the maximum number of I/O worker threads for {@link org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory#NioServerSocketChannelFactory(java.util.concurrent.Executor, java.util.concurrent.Executor, int)}
   * @param serializerFactory
   */
  public NettyServer(Map<String, Object> bootstrapOptions, String basePath, int ioThreads, SerializerFactory serializerFactory) {
    ChannelFactory factory = new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool(), 
      ioThreads);
    bootstrap = new ServerBootstrap(factory);
    bootstrap.setOptions(bootstrapOptions);
    bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
      public ChannelPipeline getPipeline() throws Exception {
        return Channels.pipeline(
          new HttpRequestDecoder(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
          new HttpResponseEncoder(),
          new ServerMethodCallHandler(allChannels, methods, methodCallbackExecutor));
      }
    });
    this.basePath = basePath;
    this.serializerFactory = serializerFactory;
  }
  
  public InetSocketAddress getLocalAddress() {
    return (InetSocketAddress) serverChannel.getLocalAddress();
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
  
  public <I, O> void register(RPC<I, O> signature, ServerMethod<I, O> method) {
    methods.put(basePath + signature.path, 
      new ServerMethodDescriptor(method, serializerFactory.createForClass(signature.outputClass), 
        serializerFactory.createForClass(signature.inputClass)));
  }

}
