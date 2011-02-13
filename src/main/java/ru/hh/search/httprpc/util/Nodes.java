package ru.hh.search.httprpc.util;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.util.concurrent.AbstractListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collection;
import static java.util.Collections.synchronizedList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.Timer;
import static ru.hh.search.httprpc.util.Timers.asTimerTask;

public class Nodes {
  public static <T, O> ListenableFuture<O> callAny(Function<T, ListenableFuture<O>> call, Iterable<T> targets, long nextTargetDelay, TimeUnit unit, Timer timer) {
    return new CallAny<T, O>(call, targets, nextTargetDelay, unit, timer);
  }

  public static <T, O> ListenableFuture<Collection<O>> callEvery(Function<T, ListenableFuture<O>> call, Iterable<T> targets) {
    return new CallEvery<T, O>(call, targets);
  }

  private static class CallEvery<T, O> extends AbstractListenableFuture<Collection<O>> {
    private final List<Future<O>> futures;
    private final List<O> results;
    private final AtomicInteger inFlight;

    public CallEvery(Function<T, ListenableFuture<O>> call, Iterable<T> targets) {
      futures = synchronizedList(new ArrayList<Future<O>>());
      results = synchronizedList(new ArrayList<O>());
      inFlight = new AtomicInteger(Iterables.size(targets));

      for (T target : targets) {
        ListenableFuture<O> future = call.apply(target);
        new FutureListener<O>(future) {
          protected void success(O result) {
            results.add(result);
          }

          protected void done() {
            if (inFlight.decrementAndGet() == 0)
              set(results);
          }
        };
        futures.add(future);
      }
    }

    /**
     * This method breaks a contract of Future. Instead of actually cancelling it,
     * it stops further computations and marks future as complete, yielding results
     * aquired by that moment.
     * @param mayInterruptIfRunning ignored
     * @return always true
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
      for(Future<O> future : futures)
        future.cancel(true);
      return true;
    }
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
      // This (and another sync in done()) both protects the list and ensures no new invocations after finishing with Future for whatever reason
      synchronized(invocations) {
        if (!isDone() && targets.hasNext()) {
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

          invocations.add(new Invocation(future, timeout));
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
