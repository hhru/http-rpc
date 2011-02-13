package ru.hh.search.httprpc;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import static java.lang.String.format;
import static java.lang.System.out;
import java.net.InetSocketAddress;
import static java.util.Arrays.asList;
import java.util.List;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;
import ru.hh.search.httprpc.netty.TcpOptions;
import ru.hh.search.httprpc.util.FutureListener;
import ru.hh.search.httprpc.util.Nodes;

interface SampleAPI {
  RPC<String, Integer> COUNT_MATCHES = RPC.signature("countMatches", String.class, Integer.class);
}

public class Example {
  public static void main(String[] args) {
    NettyClient client = new NettyClient(TcpOptions.create(), "", 2, new JavaSerializerFactory());
    NettyServer server = new NettyServer(TcpOptions.create(), "", 2, new JavaSerializerFactory());

    server.register(SampleAPI.COUNT_MATCHES, new ServerMethod<String, Integer>() {
      public ListenableFuture<Integer> call(Envelope envelope, String argument) {
        return Futures.immediateFuture(argument.length());
      }
    });

    final ClientMethod<String, Integer> countMatches = client.createMethod(SampleAPI.COUNT_MATCHES);

    InetSocketAddress s0m0 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s0m1 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s0m2 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s1m0 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s1m1 = new InetSocketAddress("127.0.0.1", 9090);

    List<List<InetSocketAddress>> geometry = asList(
        asList(s0m0, s0m1, s0m2),
        asList(s1m0, s1m1)
    );

    Timer timer = new HashedWheelTimer(5, MILLISECONDS);

    final Envelope envelope = new Envelope(100, "requestid");
    final String query = "query";

    List<InetSocketAddress> s0 = geometry.get(0);

    ListenableFuture<Integer> result = Nodes.callAny(
        qweqwe(countMatches, envelope, query),
        s0,
        20, MILLISECONDS,
        timer
    );
  }

  private static <I, O> Function<InetSocketAddress, ListenableFuture<O>> qweqwe(final ClientMethod<I, O> method, final Envelope envelope, final I input) {
    return new Function<InetSocketAddress, ListenableFuture<O>>() {
      public ListenableFuture<O> apply(final InetSocketAddress address) {
        return method.call(address, envelope, input);
      }
    };
  }

  private static <I, O> Function<InetSocketAddress, ListenableFuture<O>> qweqweTracked(final ClientMethod<I, O> method, final Envelope envelope, final I input) {
    return new Function<InetSocketAddress, ListenableFuture<O>>() {
      public ListenableFuture<O> apply(final InetSocketAddress address) {
        final ListenableFuture<O> future = method.call(address, envelope, input);
        new FutureListener<O>(future) {
          protected void success(O result) {
            out.println(format("Log: %s -> %s", address, result));
          }

          protected void exception(Throwable exception) {
            out.println(format("Log: %s -> %s", address, exception.getMessage()));
          }

          protected void cancelled() {
            out.println(format("Log: %s -> cancelled", address));
          }
        };
        return future;
      }
    };
  }
}
