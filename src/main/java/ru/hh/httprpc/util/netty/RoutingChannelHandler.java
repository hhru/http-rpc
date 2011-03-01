package ru.hh.httprpc.util.netty;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import java.util.Map;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

public class RoutingChannelHandler extends SimpleChannelUpstreamHandler {
  private final Map<String, Supplier<? extends ChannelHandler>> routes;

  public RoutingChannelHandler(Map<String, Supplier<? extends ChannelHandler>> routes) {
    this.routes = routes;
  }

  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    HttpRequest request = (HttpRequest) e.getMessage();
    String uri = request.getUri();

    for (Map.Entry<String, Supplier<? extends ChannelHandler>> route : routes.entrySet()) {
      String prefix = route.getKey();

      if (uri.startsWith(prefix)) {
        request.setUri(uri.substring(prefix.length())); // Substract matched URI prefix
        ctx.getPipeline().addLast("routed", route.getValue().get());
        ctx.sendUpstream(e);
        return;
      }

      Http.response(NOT_FOUND).
          containing(String.format(
              "Resource %s not found.\n" +
              "Available resources:\n" +
              "%s\n",
              uri,
              routes.isEmpty() ? "<none>" : Joiner.on("\n").join(routes.keySet())
          )).sendAndClose(e.getChannel());
    }
  }
}
