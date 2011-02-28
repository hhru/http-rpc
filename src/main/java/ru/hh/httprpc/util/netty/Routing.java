package ru.hh.httprpc.util.netty;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.HttpRequest;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;

public class Routing {
  public static Route route(Predicate<String> matcher, ChannelUpstreamHandler handler) {
    return new Route(matcher, handler);
  }

  public static Predicate<String> uriIs(final String uri) {
    return new Predicate<String>() {
      public boolean apply(String input) {
        return input.equals(uri);
      }

      public String toString() {
        return uri;
      }
    };
  }

  public static Predicate<String> uriStartsWith(final String prefix) {
    return new Predicate<String>() {
      public boolean apply(String input) {
        return input.startsWith(prefix);
      }

      public String toString() {
        return prefix + "*";
      }
    };
  }

  public static Predicate<String> uriEndsWith(final String suffix) {
    return new Predicate<String>() {
      public boolean apply(String input) {
        return input.endsWith(suffix);
      }

      public String toString() {
        return "*" + suffix;
      }
    };
  }

  public static Predicate<String> uriContains(final String infix) {
    return new Predicate<String>() {
      public boolean apply(String input) {
        return input.contains(infix);
      }

      public String toString() {
        return "*" + infix + "*";
      }
    };
  }

  public static Predicate<String> uriMatches(final String regex) {
    return uriMatches(Pattern.compile(regex));
  }

  public static Predicate<String> uriMatches(final Pattern pattern) {
    return new Predicate<String>() {
      public boolean apply(String input) {
        return pattern.matcher(input).matches();
      }

      public String toString() {
        return pattern.pattern();
      }
    };
  }

  public static class Route {
    final Predicate<String> matcher;
    final ChannelUpstreamHandler handler;

    Route(Predicate<String> matcher, ChannelUpstreamHandler handler) {
      this.matcher = matcher;
      this.handler = handler;
    }

    public String toString() {
      return matcher + " maps to " + handler;
    }
  }


  public static class RoutingChannelHandler extends SimpleChannelUpstreamHandler {

    List<Route> routes;

    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
      String uri = ((HttpRequest) e.getMessage()).getUri();

      for (Route route : routes)
        if (route.matcher.apply(uri)) {
          ctx.getPipeline().addLast("routed", route.handler);
          ctx.sendUpstream(e);
          return;
        }

      Http.response(NOT_FOUND).
          containing(String.format(
              "Resource %s not found.\n" +
              "Available resources:\n" +
              "%s\n",
              uri,
              routes.isEmpty() ? "<none>" : Joiner.on("\n").join(routes)
          )).sendAndClose(e.getChannel());
    }
  }
}
