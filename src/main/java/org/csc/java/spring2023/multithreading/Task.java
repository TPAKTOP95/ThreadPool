package org.csc.java.spring2023.multithreading;


import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

class Task<T> {

  private final Supplier<? extends T> task;
  final ComposableFutureImpl<T> future;

  public Task(Supplier<? extends T> taskA, ComposableFutureImpl<T> futureA) {
    task = taskA;
    future = futureA;
  }


  public Task<?> execute() {
    if (future.isPreviousFutureFailed()) {
      return future.getThenTask();
    }

    T value = null;
    ExecutionException e = null;
    try {
      value = task.get();
    } catch (Exception evalException) {
      e = new ExecutionException(evalException);
    }
    if (e != null) {
      return future.setException(e);
    }
    return future.setValue(value);
  }
}
