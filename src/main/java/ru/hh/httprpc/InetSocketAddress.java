package ru.hh.httprpc;

import java.net.InetAddress;

public class InetSocketAddress extends java.net.InetSocketAddress {

  protected String hostName;
  protected int port;

  public static InetSocketAddress createFromJavaInetSocketAddress(java.net.InetSocketAddress address) {
    if (address.isUnresolved()) {
      return new InetSocketAddress(address.getHostName(), address.getPort());
    } else {
      return new InetSocketAddress(address.getAddress(), address.getPort());
    }
  }

  public InetSocketAddress(String hostName, int port) {
    super(hostName, port);
    this.hostName = hostName;
    this.port = port;
  }

  public InetSocketAddress(InetAddress address, int port) {
    super(address, port);
    this.hostName = address.getHostAddress();
    this.port = port;
  }

  public String getHostHttpHeaderValue() {
    return hostName + ":" + String.valueOf(port);
  }
}
