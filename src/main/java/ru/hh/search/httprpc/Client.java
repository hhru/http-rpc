package ru.hh.search.httprpc;

import java.util.Map;
import java.util.concurrent.Future;

public interface Client {
  <R, A> Future<R> call(String methodName, Map<String, String> envelope, A argument); 
}
