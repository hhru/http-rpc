package ru.hh.search.httprpc.netty;

import com.google.common.util.concurrent.AbstractListenableFuture;
import org.jboss.netty.channel.ChannelFuture;

public class ClientFuture<V> extends AbstractListenableFuture<V> {
  private ChannelFuture connectFuture;

  public ClientFuture(ChannelFuture connectFuture) {
    this.connectFuture = connectFuture;
  }

  /**
   * @param mayInterruptIfRunning ignored
   */
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    boolean result = cancel();
    if (!connectFuture.cancel()) {
      connectFuture.getChannel().close();
    }
    return result;
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
