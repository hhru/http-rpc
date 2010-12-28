package ru.hh.search.httprpc;

import java.util.Map;

public interface ServerMethod {
  String getName();
  <R, A> R call(Map<String, String> envelope, A argument);
}
