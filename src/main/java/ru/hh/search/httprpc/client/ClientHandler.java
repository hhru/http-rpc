package ru.hh.search.httprpc.client;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ValueFuture;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;

public class ClientHandler<O> extends SimpleChannelUpstreamHandler {
  private final ValueFuture<O> future = ValueFuture.create();
  
  @Override
  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    HttpResponse response = (HttpResponse) e.getMessage();
    if (response.getStatus().getCode() == 200) {
      ChannelBuffer content = response.getContent();
      if (content.readable()) {
        // TODO: unserialize
        future.set((O) content.toString(CharsetUtil.UTF_8));
      }
    }
  }

  public ListenableFuture<O> getFuture() {
    return future;
  }
}
