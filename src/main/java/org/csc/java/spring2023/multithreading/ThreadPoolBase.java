package org.csc.java.spring2023.multithreading;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

abstract class ThreadPoolBase implements ThreadPool {

  /**
   * Используем ThreadFactory, чтобы избежать вызова new Thread(...) вручную.
   */
  private static final ThreadFactory threadFactory = Executors.defaultThreadFactory();

  /**
   * Возвращает поток, являющийся исполнителем задач.
   * <p>
   * Вы можете добавить в этот метод параметры, если требуется.
   */
  protected final Thread createWorkerThread() {
    return threadFactory.newThread(createWorker());
  }

  /**
   * Возвращает тело потока-исполнителя задач.
   * <p>
   * Если вы хотите передать в worker какие-то данные, добавьте параметры в этот метод.
   */
  protected abstract Runnable createWorker();
}
