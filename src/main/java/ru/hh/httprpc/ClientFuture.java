package ru.hh.httprpc;

import com.google.common.util.concurrent.AbstractListenableFuture;
import org.jboss.netty.channel.Channel;

class ClientFuture<V> extends AbstractListenableFuture<V> {
  private final Channel channel;

  public ClientFuture(Channel channel) {
    this.channel = channel;
  }

  /**
   * @param mayInterruptIfRunning ignored
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    if (cancel()) {
      channel.close();
      return true;
    } else
      return false;
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
