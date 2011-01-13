package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.AbstractListenableFuture;
import org.jboss.netty.channel.Channel;

public class ClientFuture<V> extends AbstractListenableFuture<V> {
  private final Channel channel;

  public ClientFuture(Channel channel) {
    this.channel = channel;
  }

  /**
   * @param mayInterruptIfRunning ignored
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    channel.close();
    return cancel();
  }
  
  @Override
  public boolean set(V newValue) {
    return super.set(newValue);
  }
  
  @Override
  public boolean setException(Throwable t) {
    return super.setException(t);
  }
}
