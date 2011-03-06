package ru.hh.httprpc.balancer;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import static com.google.common.collect.Iterables.transform;
import static com.google.common.collect.Lists.newArrayList;
import com.google.common.collect.MapMaker;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.ListenableFuture;
import static java.lang.Runtime.getRuntime;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import ru.hh.httprpc.util.concurrent.FutureListener;

public class LeastConnectionsBalancer<N> implements Balancer<N> {
  private final ConcurrentMap<N, NodeStat<N>> stats = new MapMaker().
      concurrencyLevel(getRuntime().availableProcessors()).
      makeComputingMap(new Function<N, NodeStat<N>>() {
        public NodeStat<N> apply(N node) {
          return new NodeStat<N>(node);
        }
      });

  private static class NodeStat<N> implements Comparable<NodeStat<N>> {
    private final N node;
    private final AtomicInteger connections = new AtomicInteger(0);

    private NodeStat(N node) {
      this.node = node;
    }

    public void connected() {
      connections.incrementAndGet();
    }

    public void disconnected() {
      connections.decrementAndGet();
    }

    public N getNode() {
      return node;
    }

    public int compareTo(NodeStat<N> stat) {
      return Ints.compare(stat.connections.get(), connections.get());
    }

    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;

      NodeStat stat = (NodeStat) o;

      return node.equals(stat.node);
    }

    public int hashCode() {
      return node.hashCode();
    }
  }

  public Iterable<N> balance(final Collection<N> nodes) {
    return new Iterable<N>() {
      public Iterator<N> iterator() {
        return new Iterator<N>() {
          List<NodeStat<N>> remainingNodes = newArrayList(transform(nodes, Functions.forMap(stats)));

          public synchronized boolean hasNext() {
            return remainingNodes.size() > 0;
          }

          public synchronized N next() {
            NodeStat<N> bestNode = Collections.max(remainingNodes);
            remainingNodes.remove(bestNode);
            return bestNode.getNode();
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

        nodeStat.connected();
        new FutureListener<O>(future) {
          protected void done() {
            nodeStat.disconnected();
          }
        };

        return future;
      }
    };
  }
}
