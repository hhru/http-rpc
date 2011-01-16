package ru.hh.search.httprpc;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LongJavaMethod implements ServerMethod<Long, Long> {
  public static final Logger logger = LoggerFactory.getLogger(LongJavaMethod.class); 
  
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
