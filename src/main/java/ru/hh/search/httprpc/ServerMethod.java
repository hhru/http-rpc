package ru.hh.search.httprpc;

import java.util.Map;

public interface ServerMethod<O, I> {
  String getPath();
  O call(Map<String, String> envelope, I argument);
}
