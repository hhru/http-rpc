package ru.hh.httprpc;

import io.netty.channel.ChannelOption;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper around bootstrap options map {@link io.netty.bootstrap.Bootstrap#option(java.util.Map)}
 */
public class TcpOptions {
  private Map<ChannelOption, Object> options = new HashMap<ChannelOption, Object>();
  private InetSocketAddress localAddress;
  private TcpOptions child;

  public static TcpOptions create() {
    return new TcpOptions();
  }

  public TcpOptions localAddress(InetSocketAddress localAddress) {
    this.localAddress = localAddress;
    return this;
  }
  
  public InetSocketAddress localAddress() {
    return localAddress;
  }

  public TcpOptions backlog(int backlog) {
    options.put(ChannelOption.SO_BACKLOG, backlog);
    return this;
  }
  
  public TcpOptions connectTimeoutMillis(int connectTimeoutMillis) {
    options.put(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis);
    return this;
  }
  
  public TcpOptions keepAlive(boolean keepAlive) {
    options.put(ChannelOption.SO_KEEPALIVE, keepAlive);
    return this;
  }
  
  public TcpOptions reuseAddress(boolean reuseAddress) {
    options.put(ChannelOption.SO_REUSEADDR, reuseAddress);
    return this;
  }
  
  public TcpOptions soLinger(int soLinger) {
    options.put(ChannelOption.SO_LINGER, soLinger);
    return this;
  }
  
  public TcpOptions tcpNoDelay(boolean tcpNoDelay) {
    options.put(ChannelOption.TCP_NODELAY, tcpNoDelay);
    return this;
  }
  
  public TcpOptions receiveBufferSize(int receiveBufferSize) {
    options.put(ChannelOption.SO_RCVBUF, receiveBufferSize);
    return this;
  }
  
  public TcpOptions sendBufferSize(int sendBufferSize) {
    options.put(ChannelOption.SO_SNDBUF, sendBufferSize);
    return this;
  }
  
  public TcpOptions child(TcpOptions child) {
    this.child = child;
    return this;
  }

  public TcpOptions child() {
    if (child == null) {
      child = new TcpOptions();
    }
    return child;
  }

  public Map<ChannelOption, Object> toMap() {
    return options;
  }
}
