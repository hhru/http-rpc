package ru.hh.httprpc.util.netty;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpChannelHandler extends SimpleChannelUpstreamHandler {
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    onHttpRequest(e.getChannel(), (HttpRequest) e.getMessage());
  }

  public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {

  }

  protected void onHttpRequest(Channel channel, HttpRequest request) throws Exception {

  }

  protected void onException(Channel channel, Throwable exception) throws Exception {

  }
}
