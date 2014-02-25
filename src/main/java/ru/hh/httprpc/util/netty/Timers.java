package ru.hh.httprpc.util.netty;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import ru.hh.httprpc.util.concurrent.CallingThreadExecutor;

public class Timers {
  public static TimerTask asTimerTask(final Runnable runnable) {
    return new TimerTask() {
      public void run(Timeout timeout) throws Exception {
        runnable.run();
      }
    };
  }

  public static TimerTask cancelFutureTask(final Future<?> future) {
    return new TimerTask() {
      public void run(Timeout timeout) throws Exception {
        future.cancel(true);
      }
    };
  }

  public static Runnable cancelTimeoutListener(final Timeout timeout) {
    return new Runnable() {
      public void run() {
        timeout.cancel();
      }
    };
  }

  public static void scheduleTimeout(ListenableFuture<?> future, Timer timer, long delay, TimeUnit unit) {
    future.addListener(
        cancelTimeoutListener(timer.newTimeout(cancelFutureTask(future), delay, unit)),
        CallingThreadExecutor.instance()
    );
  }
}
