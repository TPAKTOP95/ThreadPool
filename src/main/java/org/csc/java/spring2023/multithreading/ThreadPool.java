package org.csc.java.spring2023.multithreading;

import java.util.List;
import java.util.function.Supplier;

public interface ThreadPool {

  /**
   * Отправляет задачу на исполнение на этом тредпуле
   *
   * @param action Задача
   * @param <T>    Тип вычисляемого задачей значения
   * @return ComposableFuture, представляющий результат вычисления значения задачей
   */
  <T> ComposableFuture<T> invoke(Supplier<? extends T> action);

  /**
   * Завершает работу всех потоков, входящих в пул, дожидаясь завершения вычисления активных в
   * данный момент задач. Задачи, находящиеся в очереди, но не исполнявшиеся в момент вызова этого
   * метода, не попадают на исполнение никогда.
   */
  void shutdown();

  /**
   * Ожидание завершения работы всех потоков, входящих в пул. Не включает в себя непосредственно
   * завершение работы.
   *
   * @throws InterruptedException  если ожидающий поток был прерван во время ожидания
   * @throws IllegalStateException если перед вызовом метода не был вызван {@link #shutdown()}
   */
  void awaitFullShutdown() throws InterruptedException;

  /**
   * Метод, необходимый для тестирования вашей реализации тредпула.
   * <p>
   * Допускается использовать только для тестирования.
   *
   * @return список потоков этого пула
   */
  List<Thread> getThreads();
}
