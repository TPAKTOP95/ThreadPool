package org.csc.java.spring2023.multithreading;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ThreadPoolImpl extends ThreadPoolBase {

  private volatile boolean isWorking = true;
  private final List<Thread> workerThreads;
  private final BlockingQueue<Task<?>> tasks = new LinkedBlockingQueue<>();

  public ThreadPoolImpl(int numberOfThreads) {
    workerThreads = Stream.generate(this::createWorkerThread).limit(numberOfThreads)
        .peek(Thread::start).collect(Collectors.toList());
  }

  @Override
  protected Runnable createWorker() {
    return () -> {
      while (isWorking) {
        try {
          Task<?> thentask = tasks.take().execute();
          if (thentask != null) {
            tasks.add(thentask);
          }
        } catch (InterruptedException ignored) {
        }
      }
    };
  }

  @Override
  public List<Thread> getThreads() {
    return workerThreads;
  }

  @Override
  public <T> ComposableFuture<T> invoke(Supplier<? extends T> action) throws IllegalStateException {
    if (!isWorking) {
      throw new IllegalStateException("Can't call invoke after shutdown");
    }
    ComposableFutureImpl<T> result = new ComposableFutureImpl<>(this);
    tasks.add(new Task<>(action, result));
    return result;
  }

  @Override
  public void shutdown() {
    isWorking = false;
    workerThreads.forEach(Thread::interrupt);
  }

  @Override
  public void awaitFullShutdown() throws InterruptedException {
    if (isWorking) {
      throw new IllegalStateException("Called awaitFullShutdown before calling shutdown");
    }
    for (Thread thread : workerThreads) {
      thread.join();
    }
  }
}
