package ru.hh.httprpc;

import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class SleeperServerMethod implements ServerMethod<Long, Long> {
  private static final ExecutorService executor = Executors.newCachedThreadPool();
  private final CountDownLatch started = new CountDownLatch(1);
  private final CountDownLatch completed = new CountDownLatch(1);
  private final CountDownLatch interrupted = new CountDownLatch(1);

  public ListenableFuture<Long> call(Envelope envelope, final Long argument) {
    return JdkFutureAdapters.listenInPoolThread(executor.submit(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        try {
          started.countDown();
          Thread.sleep(argument);
          completed.countDown();
          return argument;
        } catch (InterruptedException e) {
          interrupted.countDown();
          throw e;
        }
      }
    }));
  }

  public boolean startedWithin(long time, TimeUnit unit) throws InterruptedException {
    return started.await(time, unit);
  }

  public boolean started() {
    return started.getCount() == 0;
  }

  public boolean completedWithin(long time, TimeUnit unit) throws InterruptedException {
    return completed.await(time, unit);
  }

  public boolean completed() {
    return completed.getCount() == 0;
  }

  public boolean interruptedWithin(long time, TimeUnit unit) throws InterruptedException {
    return interrupted.await(time, unit);
  }

  public boolean interrupted() {
    return interrupted.getCount() == 0;
  }
}
