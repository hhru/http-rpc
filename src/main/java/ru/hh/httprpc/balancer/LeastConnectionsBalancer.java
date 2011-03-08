package ru.hh.httprpc.balancer;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.collect.Ordering;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import static java.lang.System.nanoTime;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import ru.hh.httprpc.util.concurrent.FutureListener;

public class LeastConnectionsBalancer<N> implements Balancer<N> {
  private static class NodeStat<N> {
    public final N node;
    public final AtomicInteger connections = new AtomicInteger(0);
    public volatile long lastFault = nanoTime();

    private NodeStat(N node) {
      this.node = node;
    }
  }

  private final Map<N, NodeStat<N>> stats;
  private final long faultTimerNs;

  public LeastConnectionsBalancer(Iterable<N> nodes, long faultTimer, TimeUnit unit) {
    ImmutableMap.Builder<N, NodeStat<N>> builder = ImmutableMap.builder();
    for (N node : nodes)
      builder.put(node, new NodeStat<N>(node));
    stats = builder.build();

    faultTimerNs = unit.toNanos(faultTimer);
  }

  private Ordering<NodeStat> NODE_ORDER = new Ordering<NodeStat>() {
    public int compare(NodeStat left, NodeStat right) {
      boolean leftAlive = (nanoTime() - left.lastFault) > faultTimerNs;
      boolean rightAlive = (nanoTime() - right.lastFault) > faultTimerNs;

      if (leftAlive == rightAlive)
        return -Ints.compare(left.connections.get(), right.connections.get());
      else
        return leftAlive ? +1 : -1;
    }
  };

  public Iterable<N> balance(final Collection<N> nodes) {
    return new Iterable<N>() {
      public Iterator<N> iterator() {
        return new Iterator<N>() {
          List<NodeStat<N>> remainingNodes = newArrayList(transform(nodes, Functions.forMap(stats)));

          public synchronized boolean hasNext() {
            return remainingNodes.size() > 0;
          }

          public synchronized N next() {
            NodeStat<N> bestNode = NODE_ORDER.max(remainingNodes);
            remainingNodes.remove(bestNode);
            return bestNode.node;
          }

          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public <O> Function<N, ListenableFuture<O>> traceCalls(final Function<N, ListenableFuture<O>> nodeCallFunction) {
    return new Function<N, ListenableFuture<O>>() {
      public ListenableFuture<O> apply(N node) {
        final NodeStat nodeStat = stats.get(node);

        ListenableFuture<O> future = nodeCallFunction.apply(node);

        nodeStat.connections.incrementAndGet();
        new FutureListener<O>(future) {
          protected void exception(Throwable exception) {
            nodeStat.lastFault = nanoTime();
          }

          protected void done() {
            nodeStat.connections.decrementAndGet();
          }
        };

        return future;
      }
    };
  }
}
