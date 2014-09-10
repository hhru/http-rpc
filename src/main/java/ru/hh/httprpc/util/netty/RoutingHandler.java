package ru.hh.httprpc.util.netty;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpRequest;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static java.lang.String.format;
import java.util.Map;

@Sharable
public class RoutingHandler extends SimpleChannelInboundHandler<HttpRequest> {
  private final Map<String, ChannelHandler> routes;

  public RoutingHandler(Map<String, ChannelHandler> routes) {
    super(false);
    this.routes = ImmutableMap.copyOf(routes);
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
    String uri = request.getUri();

    for (Map.Entry<String, ChannelHandler> route : routes.entrySet()) {
      String prefix = route.getKey();
      if (uri.startsWith(prefix)) {
        request.setUri(uri.substring(prefix.length())); // Substract matched URI prefix
        ctx.pipeline().addLast(prefix, route.getValue());
        ctx.pipeline().remove(this);
        ctx.fireChannelRead(request);
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
       )).sendAndClose(ctx.channel());
  }
}