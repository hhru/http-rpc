package ru.hh.search.httprpc.util;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class FutureListener<T> implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(FutureListener.class);

  private final ListenableFuture<T> future;

  protected FutureListener(ListenableFuture<T> future) {
    this(future, CallingThreadExecutor.instance());
  }

  protected FutureListener(ListenableFuture<T> future, Executor executor) {
    this.future = future;
    future.addListener(this, executor);
  }

  public void run() {
    try {
      success(future.get());
    } catch (ExecutionException e) {
      exception(e.getCause());
    } catch (CancellationException e) {
      cancelled();
    } catch (InterruptedException e) {
      interrupted(e);
    }
  }

  protected void success(T result) {}

  protected void exception(Throwable exception) {}

  protected void cancelled() {}

  protected void interrupted(InterruptedException e) {
    log.error("Wtf?! A completed future's get() got interrupted", e);
  }
}
