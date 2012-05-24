package ru.hh.httprpc.util.concurrent;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ListenableThreadPoolExecutor extends ThreadPoolExecutor implements ListenableExecutorService{

  public ListenableThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
    super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
  }

  @Override
  protected <T> ListenableFutureTask<T> newTaskFor(Callable<T> callable) {
    return ListenableFutureTask.create(callable);
  }

  @Override
  public <T> ListenableFuture<T> submit(Callable<T> task) {
    return (ListenableFuture<T>) super.submit(task);
  }

  @Override
  public <T> ListenableFuture<T> submit(Runnable task, T result) {
    return (ListenableFuture<T>) super.submit(task, result);
  }

  @Override
  public ListenableFuture<?> submit(Runnable task) {
    return (ListenableFuture<?>) super.submit(task);
  }

}
