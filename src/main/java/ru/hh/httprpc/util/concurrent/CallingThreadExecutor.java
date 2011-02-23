package ru.hh.httprpc.util.concurrent;

import java.util.concurrent.Executor;

public class CallingThreadExecutor implements Executor {
  private static final Executor instance = new CallingThreadExecutor();

  public static Executor instance() {
    return instance;
  }

  private CallingThreadExecutor() {
  }

  public void execute(Runnable command) {
    command.run();
  }
}
