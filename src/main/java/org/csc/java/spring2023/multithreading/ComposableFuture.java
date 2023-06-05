package org.csc.java.spring2023.multithreading;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * ComposableFuture представляет собой результат асинхронного вычисления задачи.<br/> Содержит
 * методы для проверки текущего статуса вычисления и ожидания значения
 *
 * @param <T> тип значения
 */
public interface ComposableFuture<T> {

  /**
   * Статус задачи
   */
  Status getStatus();

  /**
   * Возвращает true, если задача завершила исполнение (в том числе с исключением) и у неё можно
   * вызвать get.
   * <p>
   * Если задача ещё не выполнена, то возвращает false
   */
  default boolean isReady() {
    return getStatus() != Status.NOT_FINISHED;
  }

  /**
   * Блокирующий метод, дожидается выполнения задачи и возвращает результат
   * <p>
   * Если выполнение завершилось исключением, то бросает его обернутым в ExecutionException
   * <p>
   * WARNING: Помните про такое явление как 'spurious wakeups'. Вы должны корректно обработать их.
   *
   * @throws ExecutionException   если вычисление результата текущей или зависимой задачи выбросило
   *                              исключение
   * @throws InterruptedException если ожидающий поток был прерван во время ожидания результата
   */
  T get() throws ExecutionException, InterruptedException;

  /**
   * Неблокирующий, возвращает результат, если он готов.
   * <p>
   * WARNING: Этот метод не должен брать никаких блокировок!
   *
   * @throws ExecutionException    если вычисление результата текущей или зависимой задачи выбросило
   *                               исключение
   * @throws IllegalStateException если результат ещё не вычислен
   */
  T getIfReady() throws ExecutionException;

  /**
   * Создаёт новый ComposableFuture, представляющий результат применения функции mapper к результату
   * вычисления текущего ComposableFuture (this).
   * <p>
   * Задача, созданная с помощью thenApply, должна начать исполняться не ранее, чем полностью
   * исполнится родительская задача.
   * <p>
   * Возможные ситуации:
   * <ol>
   * <li>Если родительская задача (this) ещё не выполнена, то вызов thenApply не должен добавлять
   * задачи в очередь тредпула, чтобы она не занимала потоки.</li>
   * <li>Если родительская задача уже выполнена, то вызов thenApply должен сразу же добавить
   * новую задачу в очередь тредпула.</li>
   * </ol>
   *
   * @param mapper функция, преобразующая результат этого ComposableFuture
   * @param <U>    Тип результата функции
   * @return ComposableFuture с результатом выполнения функции
   */
  <U> ComposableFuture<U> thenApply(Function<? super T, ? extends U> mapper);

  enum Status {
    NOT_FINISHED, // Не стартовала или в процессе
    FINISHED, // Полностью успешно завершилась
    FINISHED_WITH_EXCEPTION, // Завершилась из-за исключения, выброшенного задачей
  }
}
