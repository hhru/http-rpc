package ru.hh.search.httprpc;

import java.util.Map;

public interface ServerMethod<O, I> {
  String getPath();
  Class<I> getInputClass();
  O call(Map<String, String> envelope, I argument);
}
