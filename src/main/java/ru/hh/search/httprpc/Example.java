package ru.hh.search.httprpc;

import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import static java.lang.String.format;
import static java.lang.System.out;
import java.net.InetSocketAddress;
import static java.util.Arrays.asList;
import java.util.Collection;
import java.util.List;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;
import ru.hh.search.httprpc.netty.TcpOptions;
import ru.hh.search.httprpc.util.CallingThreadExecutor;
import ru.hh.search.httprpc.util.FutureListener;
import ru.hh.search.httprpc.util.Nodes;
import ru.hh.search.httprpc.util.TimerTasks;

interface SampleAPI {
  RPC<String, Integer> COUNT_MATCHES = RPC.signature("countMatches", String.class, Integer.class);
}

public class Example {
  public static void main(String[] args) throws Exception {
    NettyServer baseServer = new NettyServer(TcpOptions.create(), "", 2, new JavaSerializerFactory());
    baseServer.register(SampleAPI.COUNT_MATCHES, new ServerMethod<String, Integer>() {
      public ListenableFuture<Integer> call(Envelope envelope, String argument) {
        return Futures.immediateFuture(argument.length());
      }
    });

    NettyClient metaClient = new NettyClient(TcpOptions.create(), "", 2, new JavaSerializerFactory());
    final ClientMethod<String, Integer> countMatches = metaClient.createMethod(SampleAPI.COUNT_MATCHES);

    InetSocketAddress s0m0 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s0m1 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s0m2 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s1m0 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s1m1 = new InetSocketAddress("127.0.0.1", 9090);
    final List<List<InetSocketAddress>> geometry = asList(
        asList(s0m0, s0m1, s0m2),
        asList(s1m0, s1m1)
    );

    final Timer timer = new HashedWheelTimer(5, MILLISECONDS);

    NettyServer metaServer = new NettyServer(TcpOptions.create(), "", 2, new JavaSerializerFactory());
    metaServer.register(SampleAPI.COUNT_MATCHES, new ServerMethod<String, Integer>() {
      public ListenableFuture<Integer> call(final Envelope envelope, final String argument) {
        ListenableFuture<Collection<Integer>> future = Nodes.callEvery(new Function<List<InetSocketAddress>, ListenableFuture<Integer>>() {
          public ListenableFuture<Integer> apply(List<InetSocketAddress> targets) {
            return Nodes.callAny(
                asFunctionOfHost(countMatches, envelope, argument), // optionally - wrapWithTracer(...)
                targets,
                Math.max(envelope.timeoutMillis / targets.size(), 20), MILLISECONDS,
                timer
            );
          }
        }, geometry);

        timer.newTimeout(TimerTasks.cancelFuture(future), envelope.timeoutMillis, MILLISECONDS);

        // This should use a normal executor, as merging can be relatively costly(?) and we're better not do it on a network thread
        return Futures.compose(future, sampleFoldFunction(), CallingThreadExecutor.instance());
      }
    });

    // ?????
    // PROFIT!!!
  }

  private static Function<Collection<Integer>, Integer> sampleFoldFunction() {
    return new Function<Collection<Integer>, Integer>() {
      public Integer apply(Collection<Integer> results) {
        int sum = 0;
        for (int result : results)
          sum += result;
        return sum;
      }
    };
  }

  private static <I, O> Function<InetSocketAddress, ListenableFuture<O>> asFunctionOfHost(final ClientMethod<I, O> method, final Envelope envelope, final I input) {
    return new Function<InetSocketAddress, ListenableFuture<O>>() {
      public ListenableFuture<O> apply(final InetSocketAddress address) {
        return method.call(address, envelope, input);
      }
    };
  }

  private static <O> Function<InetSocketAddress, ListenableFuture<O>> wrapWithTracer(final Function<InetSocketAddress, ListenableFuture<O>> function) {
    return new Function<InetSocketAddress, ListenableFuture<O>>() {
      public ListenableFuture<O> apply(final InetSocketAddress address) {
        ListenableFuture<O> future = function.apply(address);
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
