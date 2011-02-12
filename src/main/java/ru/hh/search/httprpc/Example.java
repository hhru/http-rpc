package ru.hh.search.httprpc;

import static com.google.common.collect.Lists.newArrayList;
import com.google.common.util.concurrent.AbstractListenableFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;
import static java.util.Arrays.asList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import org.jboss.netty.util.TimerTask;
import ru.hh.search.httprpc.netty.NettyClient;
import ru.hh.search.httprpc.netty.NettyServer;
import ru.hh.search.httprpc.netty.TcpOptions;

public class Example {
  public static void main(String[] args) {
    NettyClient client = new NettyClient(TcpOptions.create(), "", 2, new JavaSerializerFactory());
    NettyServer server = new NettyServer(TcpOptions.create(), "", 2, new JavaSerializerFactory());

    server.register(SampleAPI.COUNT_MATCHES, new ServerMethod<String, Integer>() {
      public ListenableFuture<Integer> call(Envelope envelope, String argument) {
        return Futures.immediateFuture(argument.length());
      }
    });

    ClientMethod<String, Integer> countMatches = client.createMethod(SampleAPI.COUNT_MATCHES);

    InetSocketAddress s0m0 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s0m1 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s0m2 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s1m0 = new InetSocketAddress("127.0.0.1", 9090);
    InetSocketAddress s1m1 = new InetSocketAddress("127.0.0.1", 9090);

    List<List<InetSocketAddress>> geometry = asList(
        asList(s0m0, s0m1, s0m2),
        asList(s1m0, s1m1)
    );

    Envelope envelope = new Envelope(100, "requestid");
    String query = "query";

    Iterator<InetSocketAddress> s0iterator = geometry.get(0).iterator();

    ListenableFuture<Integer> result = new ShardCall<String, Integer>(s0iterator, countMatches, envelope, query);
  }
}

class ShardCall<I, O> extends AbstractListenableFuture<O> {
  private static final Timer TIMER = new HashedWheelTimer(10, MILLISECONDS);

  private final ClientMethod<I, O> method;
  private final Envelope envelope;
  private final I input;

  private final List<Invocation> invocations = newArrayList();

  public ShardCall(Iterator<InetSocketAddress> mirrors, ClientMethod<I, O> method, Envelope envelope, I input) {
    this.method = method;
    this.envelope = envelope;
    this.input = input;

    callNext(mirrors);
  }

  // Todo: fix   future.get() ExecutionException VS timeout    callNext race
  private void callNext(final Iterator<InetSocketAddress> mirrors) {
    if (mirrors.hasNext()) {
      InetSocketAddress host = mirrors.next();

      final ListenableFuture<O> future = method.call(host, envelope, input);

      future.addListener(
          new Runnable() {
            public void run() {
              try {
                set(future.get());
              } catch (ExecutionException e) {
                callNext(mirrors);
              } catch (InterruptedException ignore) {
              } catch (CancellationException ignore) {
              }
            }
          },
          CallingThreadExecutor.instance()
      );

      Timeout timeout = TIMER.newTimeout(
          new TimerTask() {
            public void run(Timeout timeout) throws Exception {
              callNext(mirrors);
            }
          },
          envelope.timeoutMilliseconds, MILLISECONDS
      );

      Invocation invocation = new Invocation(future, timeout);
      synchronized (invocations) {
        if (!isDone())
          invocations.add(invocation);
        else
          invocation.cancel();
      }
    }
  }

  public boolean cancel(boolean mayInterruptIfRunning) {
    return cancel();
  }


  protected void done() {
    synchronized (invocations) {
      for (Invocation invocation : invocations)
        invocation.cancel();
    }

    super.done(); // Run listeners
  }

  static class Invocation {
    private final Future<?> future;
    private final Timeout timeout;

    public Invocation(Future<?> future, Timeout timeout) {
      this.future = future;
      this.timeout = timeout;
    }

    public void cancel() {
      future.cancel(true);
      timeout.cancel();
    }
  }
}

interface SampleAPI {
  RPC<String, Integer> COUNT_MATCHES = RPC.signature("countMatches", String.class, Integer.class);
}