package ru.hh.search.httprpc;

import java.util.Map;

public class HelloMethod implements ServerMethod<String, String> {
  
  public String getName() {
    return "helloMethod";
  }

  public String call(Map<String, String> envelope, String argument) {
    return "World!";
  }
}
