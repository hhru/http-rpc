package ru.hh.httprpc.util.netty;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static java.lang.System.currentTimeMillis;

public class ChannelContextData {
  private final Channel channel;
  private final long startTimeMillis;

  private HttpRequest request;
  private HttpResponse response;
  private String baseUrl;

  public ChannelContextData(Channel channel) {
    this.channel = channel;
    startTimeMillis = currentTimeMillis();
    baseUrl = "";
  }

  public HttpRequest getRequest() {
    return request;
  }

  public void setRequest(HttpRequest request) {
    this.request = request;
  }

  public HttpResponse getResponse() {
    return response;
  }

  public void setResponse(HttpResponse response) {
    this.response = response;
  }

  public long getLifetime() {
    return currentTimeMillis() - startTimeMillis;
  }

  public InetAddress getRemoteAddress() {
    return ((InetSocketAddress) channel.getRemoteAddress()).getAddress();
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }
}
