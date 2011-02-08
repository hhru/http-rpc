package ru.hh.search.httprpc;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public class LongJavaMethod implements ServerMethod<Long, Long> {
  
  private final ExecutorService executor;

  public LongJavaMethod(ExecutorService executor) {
    this.executor = executor;
  }

  @Override
  public ListenableFuture<Long> call(Envelope envelope, final Long argument) {
    return Futures.makeListenable(executor.submit(new Callable<Long>() {
      @Override
      public Long call() throws Exception {
        Thread.sleep(argument);
        return argument;
      }
    }));
  }
}
