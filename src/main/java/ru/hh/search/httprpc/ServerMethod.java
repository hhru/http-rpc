package ru.hh.search.httprpc;

import java.util.Map;

public interface ServerMethod<R, A> {
  String getPath();
  R call(Map<String, String> envelope, A argument);
}
