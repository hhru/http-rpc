package ru.hh.search.httprpc.util;

import org.jboss.netty.util.Timeout;
import org.jboss.netty.util.TimerTask;

public class Timers {
  public static TimerTask asTimerTask(final Runnable runnable) {
    return new TimerTask() {
      public void run(Timeout timeout) throws Exception {
        runnable.run();
      }
    };
  }
}
