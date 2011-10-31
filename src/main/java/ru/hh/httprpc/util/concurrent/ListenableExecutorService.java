package ru.hh.httprpc.util.concurrent;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

public interface ListenableExecutorService extends ExecutorService {

  <T> ListenableFuture<T> submit(Callable<T> task);

  <T> ListenableFuture<T> submit(Runnable task, T result);

  ListenableFuture<?> submit(Runnable task);

}
