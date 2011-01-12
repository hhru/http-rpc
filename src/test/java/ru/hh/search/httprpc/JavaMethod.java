package ru.hh.search.httprpc;

import java.util.Map;

public class JavaMethod implements ServerMethod<String, String> {
  
  @Override
  public Class<String> getInputClass() {
    return String.class;
  }
  
  public String call(Envelope envelope, String argument) {
    return argument.toUpperCase();
  }
}
