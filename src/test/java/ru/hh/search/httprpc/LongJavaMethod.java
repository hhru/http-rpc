package ru.hh.search.httprpc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class LongJavaMethod implements ServerMethod<Long, Long> {
  private final CountDownLatch completed;
  private final CountDownLatch interrupted;
  private final ExecutorService executor;

  public LongJavaMethod(ExecutorService executor, CountDownLatch completed, CountDownLatch interrupted) {
    this.executor = executor;
    this.completed = completed;
    this.interrupted = interrupted;
  }

  @Override
  public ListenableFuture<Long> call(Envelope envelope, final Long argument) {
    return Futures.makeListenable(executor.submit(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        try {
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
}
