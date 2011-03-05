package ru.hh.httprpc.util.netty;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public abstract class HttpHandler extends SimpleChannelUpstreamHandler {
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    Channel channel = e.getChannel();
    HttpRequest request = (HttpRequest) e.getMessage();
    Http.UrlDecoder url = new Http.UrlDecoder(request.getUri());

    requestReceived(channel, request, url);
  }

  protected abstract void requestReceived(Channel channel, HttpRequest request, Http.UrlDecoder url) throws Exception;
}
