package ru.hh.httprpc;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;
import ru.hh.httprpc.balancer.Balancer;
import ru.hh.httprpc.balancer.LeastConnectionsBalancer;
import ru.hh.httprpc.serialization.JavaSerializer;
import ru.hh.httprpc.util.concurrent.AsyncToolbox;
import ru.hh.httprpc.util.concurrent.CallingThreadExecutor;
import ru.hh.httprpc.util.concurrent.FutureListener;
import ru.hh.httprpc.util.netty.RoutingHandler;
import ru.hh.httprpc.util.netty.Timers;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;

import static java.lang.String.format;
import static java.lang.System.out;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

interface SampleAPI {
  RPC<String, Integer> COUNT_MATCHES = RPC.signature("/countMatches", String.class, Integer.class);
}

public class Example {
  public static void main(String[] args) throws Exception {

    RPCHandler baseHandler = new RPCHandler(new JavaSerializer());
    baseHandler.register(SampleAPI.COUNT_MATCHES, new ServerMethod<String, Integer>() {
      public ListenableFuture<Integer> call(Envelope envelope, String argument) {
        return Futures.immediateFuture(argument.length());
      }
    });
    RoutingHandler baseRouter = new RoutingHandler(
      ImmutableMap.<String, ChannelHandler>builder().put("/base", baseHandler).build());
    HTTPServer baseServer = new HTTPServer(TcpOptions.create(), 2, baseRouter);

    RPCClient metaClient = new RPCClient(TcpOptions.create(), "", 2, new JavaSerializer());
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
//    final Balancer<InetSocketAddress> balancer = new RandomRobinBalancer<InetSocketAddress>();
    final Balancer<InetSocketAddress> balancer = new LeastConnectionsBalancer<InetSocketAddress>(Iterables.concat(geometry), 1, SECONDS);

    RPCHandler metaHandler = new RPCHandler(new JavaSerializer());
    metaHandler.register(SampleAPI.COUNT_MATCHES, new ServerMethod<String, Integer>() {
      public ListenableFuture<Integer> call(final Envelope envelope, final String argument) {
        final Function<InetSocketAddress, ListenableFuture<Integer>> callNodeFn = balancer.traceCalls(asFunctionOfHost(countMatches, envelope, argument));

        ListenableFuture<Collection<Integer>> future = AsyncToolbox.callEvery(new Function<List<InetSocketAddress>, ListenableFuture<Integer>>() {
          public ListenableFuture<Integer> apply(List<InetSocketAddress> targets) {
            return AsyncToolbox.callAny(
                callNodeFn, // optionally - logCalls(...)
                balancer.balance(targets),
                Math.max(envelope.timeoutMillis / targets.size(), 20), MILLISECONDS,
                timer
            );
          }
        }, geometry);

        Timers.scheduleTimeout(future, timer, envelope.timeoutMillis, MILLISECONDS);

        // This should use a normal executor, as merging can be relatively costly(?) and we're better not do it on a network thread
        return Futures.transform(future, sampleFoldFunction(), CallingThreadExecutor.instance());
      }
    });
    RoutingHandler metaRouter = new RoutingHandler(
      ImmutableMap.<String, ChannelHandler>builder().put("/meta", metaHandler).build());
    
    HTTPServer metaServer = new HTTPServer(TcpOptions.create(), 2, metaRouter);

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

  private static <O> Function<InetSocketAddress, ListenableFuture<O>> logCalls(final Function<InetSocketAddress, ListenableFuture<O>> function) {
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
