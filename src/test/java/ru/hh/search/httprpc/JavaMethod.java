package ru.hh.search.httprpc;

import java.util.Map;

public class JavaMethod implements ServerMethod<String, String> {
  
  public String getPath() {
    return "/javaMethod";
  }

  @Override
  public Class<String> getInputClass() {
    return String.class;
  }
  
  public String call(Map<String, String> envelope, String argument) {
    return argument.toUpperCase();
  }
}
