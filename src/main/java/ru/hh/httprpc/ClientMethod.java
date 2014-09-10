package ru.hh.httprpc;

import com.google.common.util.concurrent.ListenableFuture;

public interface ClientMethod<I, O> {
  ListenableFuture<O> call(InetSocketAddress address, Envelope envelope, I input);
}
