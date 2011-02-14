package ru.hh.search.httprpc.util;

import java.util.concurrent.Future;
import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

public class TimerTasks {
  public static TimerTask asTimerTask(final Runnable runnable) {
    return new TimerTask() {
      public void run(Timeout timeout) throws Exception {
        runnable.run();
      }
    };
  }

  public static TimerTask cancelFuture(final Future<?> future) {
    return new TimerTask() {
      public void run(Timeout timeout) throws Exception {
        future.cancel(true);
      }
    };
  }
}
