package ru.hh.search.httprpc.netty;

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
  
  public TcpOptions setLocalAddress(InetSocketAddress localAddress) {
    options.put("localAddress", localAddress);
    return this;
  }
  
  public Map<String, Object> toMap() {
    return options;
  }
}
