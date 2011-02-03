package ru.hh.search.httprpc;

import com.google.common.base.Throwables;

public class LongJavaMethod implements ServerMethod<Long, Long> {
  @Override
  public Long call(Envelope envelope, Long argument) {
    try {
      Thread.sleep(argument);
      return argument;
    } catch (InterruptedException e) {
      throw Throwables.propagate(e);
    }
  }
}
