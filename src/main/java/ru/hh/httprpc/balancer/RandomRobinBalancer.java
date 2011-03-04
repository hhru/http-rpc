package ru.hh.httprpc.balancer;

import com.google.common.base.Function;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collection;
import static java.util.Collections.shuffle;
import java.util.List;
import java.util.Random;

public class RandomRobinBalancer<N> implements Balancer<N> {
  private final Random rnd = new Random();

  public Iterable<N> balance(Collection<N> nodes) {
    List<N> shuffledNodes = newArrayList(nodes);
    shuffle(shuffledNodes, rnd);
    return shuffledNodes;
  }

  public <O> Function<N, ListenableFuture<O>> traceCalls(Function<N, ListenableFuture<O>> nodeCallFunction) {
    return nodeCallFunction;
  }
}
