package ru.hh.httprpc.balancer;

import com.google.common.base.Function;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;

public interface Balancer<N> {
  Iterable<N> balance(Collection<N> nodes);

  <O> Function<N, ListenableFuture<O>> traceCalls(Function<N, ListenableFuture<O>> nodeCallFunction);
}
