package ru.hh.httprpc.util.concurrent;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class RunnableOnce implements Runnable {
  private final AtomicBoolean hasRun = new AtomicBoolean(false);

  public static Runnable wrap(final Runnable runnable) {
    return new RunnableOnce() {
      protected void doRun() {
        runnable.run();
      }
    };
  }

  public void run() {
    if (hasRun.compareAndSet(false, true)) {
      doRun();
    }
  }

  protected abstract void doRun();
}
