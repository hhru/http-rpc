package ru.hh.search.httprpc.util;

import com.google.common.base.Function;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.util.concurrent.AbstractListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import static ru.hh.search.httprpc.util.Timers.asTimerTask;

public class Nodes {
  public static <T, O> ListenableFuture<O> callAny(Function<T, ListenableFuture<O>> call, Iterable<T> targets, long nextTargetDelay, TimeUnit unit, Timer timer) {
    return new CallAny<T, O>(call, targets, nextTargetDelay, unit, timer);
  }

  public static <T, O> ListenableFuture<Collection<O>> callEvery(Function<T, ListenableFuture<O>> call, Iterable<T> targets) {
    return null;
  }

  private static class CallAny<I, O> extends AbstractListenableFuture<O> {
    private final List<Invocation> invocations = newArrayList();
    private final Function<I, ListenableFuture<O>> call;

    private final long nextTargetDelay;
    private final TimeUnit unit;
    private final Timer timer;

    public CallAny(Function<I, ListenableFuture<O>> call, Iterable<I> targets, long nextTargetDelay, TimeUnit unit, Timer timer) {
      this.call = call;

      this.nextTargetDelay = nextTargetDelay;
      this.unit = unit;
      this.timer = timer;

      callNext(targets.iterator());
    }

    private void callNext(final Iterator<I> targets) {
      if (targets.hasNext()) {
        I target = targets.next();

        final Runnable callNextOnce = new RunnableOnce() {
          protected void doRun() {
            callNext(targets);
          }
        };

        final ListenableFuture<O> future = call.apply(target);

        new FutureListener<O>(future) {
          protected void success(O result) {
            set(result);
          }

          protected void exception(Throwable exception) {
            callNextOnce.run();
          }
        };

        Timeout timeout = timer.newTimeout(asTimerTask(callNextOnce), nextTargetDelay, unit);

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

    private static class Invocation {
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
}
