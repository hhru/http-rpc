package ru.hh.httprpc.util.netty;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import static java.lang.String.format;
import java.util.Map;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.ChannelHandler.Sharable;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

@Sharable
public class RoutingHandler extends SimpleChannelUpstreamHandler {
  private final Map<String, ChannelHandler> routes;

  public RoutingHandler(Map<String, ChannelHandler> routes) {
    this.routes = ImmutableMap.copyOf(routes);
  }

  public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
    HttpRequest request = (HttpRequest) e.getMessage();
    String uri = request.getUri();

    for (Map.Entry<String, ChannelHandler> route : routes.entrySet()) {
      String prefix = route.getKey();
      if (uri.startsWith(prefix)) {
        request.setUri(uri.substring(prefix.length())); // Substract matched URI prefix
        ctx.getPipeline().addLast(prefix, route.getValue());
        ctx.sendUpstream(e);
        return;
      }
    }
    
    Http.response(NOT_FOUND).
      containing(format(
          "resource '%s' not found.\n" +
          "available resources:\n" +
          "%s\n",
          uri,
          routes.isEmpty() ? "<none>" : Joiner.on("\n").join(routes.keySet())
      )).sendAndClose(e.getChannel());
  }
}