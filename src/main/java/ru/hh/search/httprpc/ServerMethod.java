package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;

public interface ServerMethod<O, I> {
  ListenableFuture<O> call(Envelope envelope, I argument);
}
