package org.csc.java.spring2023.multithreading;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

class ComposableFutureImpl<T> implements ComposableFuture<T> {

  private final CountDownLatch latch = new CountDownLatch(1);
  private T value = null;

  private final ThreadPool threadPool;
  private ExecutionException exception = null;
  private volatile Status status = Status.NOT_FINISHED;

  private volatile boolean isPreviousFutureFailed = false;
  private final ConcurrentLinkedQueue<Task<?>> thenTaskList = new ConcurrentLinkedQueue<>();

  public ComposableFutureImpl(ThreadPool threadPool) {
    this.threadPool = threadPool;
  }

  @Override
  public Status getStatus() {
    return status;
  }

  @Override
  public T get() throws ExecutionException, InterruptedException {
    if (status == Status.NOT_FINISHED) {
      latch.await();
    }
    return getIfReady();
  }

  @Override
  public T getIfReady() throws ExecutionException {
    if (status == Status.NOT_FINISHED) {
      throw new IllegalStateException("Value not ready");
    }
    if (exception != null) {
      throw exception;
    }
    return value;
  }

  @Override
  public <U> ComposableFuture<U> thenApply(Function<? super T, ? extends U> mapper) {
    ComposableFuture<U> thenFuture = thenApplyOnFinishedFuture(mapper);
    if (thenFuture != null) {
      return thenFuture;
    }
    synchronized (this) {
      ComposableFuture<U> thenFutureDoubleCheck = thenApplyOnFinishedFuture(mapper);
      if (thenFutureDoubleCheck != null) {
        return thenFutureDoubleCheck;
      }
      ComposableFutureImpl<U> thenComposableFuture = new ComposableFutureImpl<>(threadPool);
      thenTaskList.add(new Task<>(() -> mapper.apply(value), thenComposableFuture));
      return thenComposableFuture;
    }
  }

  private <U> ComposableFuture<U> thenApplyOnFinishedFuture(
      Function<? super T, ? extends U> mapper) {
    if (status == Status.FINISHED) {
      return threadPool.invoke(() -> mapper.apply(value));
    } else if (status == Status.FINISHED_WITH_EXCEPTION) {
      ComposableFutureImpl<U> thenComposableFuture = new ComposableFutureImpl<>(threadPool);
      thenComposableFuture.exception = new ExecutionException("Parent finished with exception",
          exception);
      thenComposableFuture.status = Status.FINISHED_WITH_EXCEPTION;
      thenComposableFuture.latch.countDown();
      return thenComposableFuture;
    }
    return null;
  }

  public synchronized Task<?> setValue(T setValue) {
    value = setValue;
    status = Status.FINISHED;
    latch.countDown();
    return getThenTask();
  }

  public synchronized Task<?> setException(ExecutionException e) {
    exception = e;
    status = Status.FINISHED_WITH_EXCEPTION;
    latch.countDown();
    return getThenTask();
  }

  public Task<?> getThenTask() {
    Task<?> thenTask = thenTaskList.poll();
    if (thenTask != null && exception != null) {
      thenTask.future.exception = new ExecutionException("Parent finished with exception",
          exception);
      thenTask.future.status = Status.FINISHED_WITH_EXCEPTION;
      thenTask.future.isPreviousFutureFailed = true;
      thenTask.future.latch.countDown();
      latch.countDown();
    }
    return thenTask;
  }

  public boolean isPreviousFutureFailed() {
    return isPreviousFutureFailed;
  }

}
