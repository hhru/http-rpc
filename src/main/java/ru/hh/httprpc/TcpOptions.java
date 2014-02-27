package ru.hh.httprpc;

import io.netty.bootstrap.AbstractBootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper around bootstrap options map {@link io.netty.bootstrap.Bootstrap#option(io.netty.channel.ChannelOption, Object)}
 */
public class TcpOptions<T extends TcpOptions> {
  private final T self;

  private TcpOptions() {
    self = (T) this;
  }

  public static class ChildOptions extends TcpOptions<ChildOptions> {
    @Override
    public ChildOptions child(ChildOptions child) {
      throw new UnsupportedOperationException();
    }
    @Override
    public void initializeBootstrap(AbstractBootstrap bootstrap) {
      throw new UnsupportedOperationException();
    }
    @Override
    public void initializeBootstrap(ServerBootstrap bootstrap) {
      throw new UnsupportedOperationException();
    }
  }
  protected Map<ChannelOption, Object> options = new HashMap<ChannelOption, Object>();
  private InetSocketAddress localAddress;
  private ChildOptions child;

  public static TcpOptions<TcpOptions> create() {
    return new TcpOptions<TcpOptions>();
  }

  public static ChildOptions newChildOptions() {
    return new ChildOptions();
  }

  public T localAddress(InetSocketAddress localAddress) {
    this.localAddress = localAddress;
    return self;
  }
  
  public InetSocketAddress localAddress() {
    return localAddress;
  }

  public T backlog(int backlog) {
    options.put(ChannelOption.SO_BACKLOG, backlog);
    return self;
  }
  
  public T connectTimeoutMillis(int connectTimeoutMillis) {
    options.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
    return self;
  }
  
  public T keepAlive(boolean keepAlive) {
    options.put(ChannelOption.SO_KEEPALIVE, keepAlive);
    return self;
  }
  
  public T reuseAddress(boolean reuseAddress) {
    options.put(ChannelOption.SO_REUSEADDR, reuseAddress);
    return self;
  }
  
  public T soLinger(int soLinger) {
    options.put(ChannelOption.SO_LINGER, soLinger);
    return self;
  }
  
  public T tcpNoDelay(boolean tcpNoDelay) {
    options.put(ChannelOption.TCP_NODELAY, tcpNoDelay);
    return self;
  }
  
  public T receiveBufferSize(int receiveBufferSize) {
    options.put(ChannelOption.SO_RCVBUF, receiveBufferSize);
    return self;
  }
  
  public T sendBufferSize(int sendBufferSize) {
    options.put(ChannelOption.SO_SNDBUF, sendBufferSize);
    return self;
  }

  public T child(ChildOptions child) {
    this.child = child;
    return self;
  }

  public void initializeBootstrap(AbstractBootstrap bootstrap) {
    for (Map.Entry<ChannelOption, Object> entry : options.entrySet()) {
      bootstrap.option(entry.getKey(), entry.getValue());
    }
  }

  public void initializeBootstrap(ServerBootstrap bootstrap) {
    initializeBootstrap((AbstractBootstrap) bootstrap);
    if (child != null) {
      for (Map.Entry<ChannelOption, Object> entry : child.options.entrySet()) {
        bootstrap.childOption(entry.getKey(), entry.getValue());
      }
    }
  }
}
