package org.csc.java.spring2023.multithreading;

public class ThreadPoolFactory {

  private ThreadPoolFactory() {
  }

  public static ThreadPool createThreadPool(int numberOfThreads) {
    return new ThreadPoolImpl(numberOfThreads);
  }
}
