package ru.hh.httprpc.util.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

public abstract class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  @Override
  public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
    Channel channel = ctx.channel();
    Http.UrlDecoder url = new Http.UrlDecoder(request.getUri());

    requestReceived(channel, request, url);
  }

  protected abstract void requestReceived(Channel channel, FullHttpRequest request, Http.UrlDecoder url) throws Exception;
}
