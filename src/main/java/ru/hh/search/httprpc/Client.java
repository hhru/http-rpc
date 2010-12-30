package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Map;

public interface Client {
  <O, I> ListenableFuture<O> call(String path, Map<String, String> envelope, I argument); 
}
