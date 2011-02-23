package ru.hh.httprpc;

import com.google.common.util.concurrent.ListenableFuture;

public interface ServerMethod<I, O> {
  ListenableFuture<O> call(Envelope envelope, I argument);
}
