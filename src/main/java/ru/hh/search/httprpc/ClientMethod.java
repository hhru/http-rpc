package ru.hh.search.httprpc;

import com.google.common.util.concurrent.ListenableFuture;
import java.net.InetSocketAddress;

public interface ClientMethod<O, I> {
  ListenableFuture<O> call(InetSocketAddress address, Envelope envelope, I input); 
}
