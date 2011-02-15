package ru.hh.search.httprpc;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Wrapper around bootstrap options map {@link org.jboss.netty.bootstrap.Bootstrap#setOptions(java.util.Map)}
 */
public class TcpOptions {
  private Map<String, Object> options = new HashMap<String, Object>();

  public static TcpOptions create() {
    return new TcpOptions();
  }
  
  public TcpOptions localAddress(InetSocketAddress localAddress) {
    options.put("localAddress", localAddress);
    return this;
  }
  
  public TcpOptions backlog(int backlog) {
    options.put("backlog", backlog);
    return this;
  }
  
  public TcpOptions connectTimeoutMillis(int connectTimeoutMillis) {
    options.put("connectTimeoutMillis", connectTimeoutMillis);
    return this;
  }
  
  public TcpOptions keepAlive(boolean keepAlive) {
    options.put("keepAlive", keepAlive);
    return this;
  }
  
  public TcpOptions reuseAddress(boolean reuseAddress) {
    options.put("reuseAddress", reuseAddress);
    return this;
  }
  
  public TcpOptions soLinger(int soLinger) {
    options.put("soLinger", soLinger);
    return this;
  }
  
  public TcpOptions tcpNoDelay(boolean tcpNoDelay) {
    options.put("tcpNoDelay", tcpNoDelay);
    return this;
  }
  
  public TcpOptions receiveBufferSize(int receiveBufferSize) {
    options.put("receiveBufferSize", receiveBufferSize);
    return this;
  }
  
  public TcpOptions sendBufferSize(int sendBufferSize) {
    options.put("sendBufferSize", sendBufferSize);
    return this;
  }
  
  public TcpOptions child(TcpOptions childOptions) {
    for (Map.Entry<String, Object> entry : childOptions.toMap().entrySet()) {
      options.put("child." + entry.getKey(), entry.getValue());
    }
    return this;
  }
  
  public Map<String, Object> toMap() {
    return options;
  }
}
