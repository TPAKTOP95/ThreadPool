package org.csc.java.spring2023.multithreading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.csc.java.spring2023.multithreading.ComposableFuture.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
public class ThreadPoolTests {

  private ThreadPool createThreadPool(int count) {
    return ThreadPoolFactory.createThreadPool(count);
  }

  private void useThreadPool(int count, Consumer<ThreadPool> consumer) {
    ThreadPool pool = createThreadPool(count);
    useThreadPool(pool, consumer);
  }

  private void useThreadPool(ThreadPool pool, Consumer<ThreadPool> consumer) {
    try {
      consumer.accept(pool);
    } finally {
      pool.shutdown();
    }
  }

  /**
   * Поток, на котором вызван этот метод, заснёт на {@code millis} миллисекунд.
   */
  private void sleepForMillis(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private <T> T getValue(ComposableFuture<T> future) {
    try {
      return future.get();
    } catch (ExecutionException | InterruptedException e) {
      fail("Unexpected exception", e);

      // unreachable
      throw new AssertionError();
    }
  }

  @Test
  @DisplayName("Pool is initialized and shutdown")
  void testInit() {
    useThreadPool(4, pool -> {
      assertThat(pool.getThreads()).hasSize(4);
      assertThat(pool.getThreads()).allMatch(Thread::isAlive);
    });
  }

  @Test
  @DisplayName("Lambda is called")
  void testLambdaIsCalled() {
    useThreadPool(1, pool -> {
      int[] result = {0};

      pool.invoke(() -> {
        result[0] = 42;
        return null;
      });

      sleepForMillis(500);

      assertThat(result).containsExactly(42);
    });
  }

  @Test
  @DisplayName("Future is awaited")
  void testFutureAwait() {
    useThreadPool(1, pool -> {
      int[] result = {0};

      ComposableFuture<?> resultFuture = pool.invoke(() -> {
        result[0] = 42;
        return null;
      });

      getValue(resultFuture);

      assertThat(result).containsExactly(42);
    });
  }

  @Test
  @DisplayName("Lambda result from future is correct")
  void testResult() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> resultFuture = pool.invoke(() -> 42);

      sleepForMillis(500);
      Integer result = getValue(resultFuture);

      assertThat(result).isEqualTo(42);
    });
  }

  @Test
  @DisplayName("Lambda result from future is correct if ready")
  void testResultIfReady() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> resultFuture = pool.invoke(() -> 42);

      sleepForMillis(500);
      Integer result = null;

      try {
        result = resultFuture.getIfReady();
      } catch (ExecutionException e) {
        fail("Unexpected exception; resultFuture should be successfully finished by now", e);
      }

      assertThat(result).isEqualTo(42);
    });
  }

  @Test
  @DisplayName("Lambda result from future is correct after await")
  void testResultAwait() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> resultFuture = pool.invoke(() -> 42);

      Integer result = getValue(resultFuture);

      assertThat(result).isEqualTo(42);
    });
  }

  @Test
  @DisplayName("Exception from lambda is wrapped into ExecutionException (get)")
  void testExceptionHandling() {
    useThreadPool(1, pool -> {
      RuntimeException exceptionFromFuture = new RuntimeException("Exception from future");

      ComposableFuture<Object> resultFuture = pool.invoke(() -> {
        throw exceptionFromFuture;
      });

      sleepForMillis(500);

      try {
        resultFuture.get();
        fail("Exception from lambda isn't handled");
      } catch (ExecutionException e) {
        assertThat(e.getCause()).isSameAs(exceptionFromFuture);
      } catch (InterruptedException e) {
        fail("Unexpected exception", e);
      }
    });
  }

  @Test
  @DisplayName("Exception from lambda is wrapped into ExecutionException (getIfReady)")
  void testExceptionHandlingIfReady() {
    useThreadPool(1, pool -> {
      RuntimeException exceptionFromFuture = new RuntimeException("Exception from future");

      ComposableFuture<Object> resultFuture = pool.invoke(() -> {
        throw exceptionFromFuture;
      });

      sleepForMillis(500);

      try {
        resultFuture.getIfReady();
        fail("Exception from lambda isn't handled");
      } catch (ExecutionException e) {
        assertThat(e.getCause()).isSameAs(exceptionFromFuture);
      }
    });
  }


  @Test
  @DisplayName("Exception from lambda is wrapped into ExecutionException (get, no sleep)")
  void testExceptionHandlingAwait() {
    useThreadPool(1, pool -> {
      RuntimeException exceptionFromFuture = new RuntimeException("Exception from future");

      ComposableFuture<Object> resultFuture = pool.invoke(() -> {
        throw exceptionFromFuture;
      });

      try {
        resultFuture.get();
        fail("Exception from lambda isn't handled");
      } catch (ExecutionException e) {
        assertThat(e.getCause()).isSameAs(exceptionFromFuture);
      } catch (InterruptedException e) {
        fail("Unexpected exception", e);
      }
    });
  }

  @Test
  @DisplayName("Test status (initial)")
  void testInitialStatus() {
    useThreadPool(1, pool -> {

      pool.invoke(() -> {
        sleepForMillis(500);
        return null;
      });

      ComposableFuture<Integer> result = pool.invoke(() -> 42);

      assertThat(result.getStatus()).isEqualTo(Status.NOT_FINISHED);
      assertThat(result.isReady()).isFalse();
    });
  }

  @Test
  @DisplayName("Status (successful)")
  void testSuccessfulStatus() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> result = pool.invoke(() -> 42);

      sleepForMillis(500);

      assertThat(result.getStatus()).isEqualTo(Status.FINISHED);
      assertThat(result.isReady()).isTrue();
    });
  }

  @Test
  @DisplayName("Status (exception)")
  void testFailedStatus() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> result = pool.invoke(() -> {
        throw new RuntimeException();
      });

      sleepForMillis(500);

      assertThat(result.getStatus()).isEqualTo(Status.FINISHED_WITH_EXCEPTION);
      assertThat(result.isReady()).isTrue();
    });
  }

  @Test
  @DisplayName("Awaited status (successful)")
  void testSuccessfulAwaitedStatus() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> result = pool.invoke(() -> 42);

      getValue(result);

      assertThat(result.getStatus()).isEqualTo(Status.FINISHED);
      assertThat(result.isReady()).isTrue();
    });
  }


  @Test
  @DisplayName("Awaited status (exception)")
  void testFailedAwaitedStatus() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> result = pool.invoke(() -> {
        throw new RuntimeException();
      });

      try {
        result.get();
      } catch (ExecutionException ignored) {
      } catch (InterruptedException e) {
        fail("Unexpected exception", e);
      }

      assertThat(result.getStatus()).isEqualTo(Status.FINISHED_WITH_EXCEPTION);
      assertThat(result.isReady()).isTrue();
    });
  }

  @Test
  @DisplayName("Shutdown stops threads")
  void testShutdown() {
    ThreadPool pool = createThreadPool(5);

    try {

      for (int i = 0; i < 10; ++i) {
        pool.invoke(() -> {
          sleepForMillis(300);
          return null;
        });
      }

    } finally {
      pool.shutdown();
    }

    List<Thread> threads = pool.getThreads();
    sleepForMillis(1000);

    assertThat(threads).noneMatch(Thread::isAlive);
  }

  @Test
  @DisplayName("AwaitFullShutdown on live pool throws IllegalStateException")
  void testAwaitFullShutdownThrowsException() {
    useThreadPool(1, pool -> assertThatThrownBy(pool::awaitFullShutdown).isInstanceOf(
        IllegalStateException.class));
  }

  @Test
  @DisplayName("Shutdown stops threads (await)")
  void testAwaitFullShutdown() throws InterruptedException {
    ThreadPool pool = createThreadPool(5);
    try {

      for (int i = 0; i < 10; ++i) {
        pool.invoke(() -> {
          sleepForMillis(300);
          return null;
        });
      }

    } finally {
      pool.shutdown();
    }

    List<Thread> threads = pool.getThreads();
    pool.awaitFullShutdown();

    assertThat(threads).noneMatch(Thread::isAlive);
  }

  @Test
  @DisplayName("Remaining tasks are not executed after shutdown")
  void testNoTasksAfterShutdown() throws ExecutionException, InterruptedException {
    ThreadPool pool = createThreadPool(1);

    CountDownLatch countDownLatch = new CountDownLatch(1);
    ComposableFuture<Integer> slowFirstTask = pool.invoke(() -> {
      try {
        countDownLatch.countDown();
        Thread.sleep(500);
      } catch (InterruptedException ignored) {
      }
      return 5;
    });
    ComposableFuture<Integer> fastSecondTask = pool.invoke(() -> 5);

    countDownLatch.await();
    pool.shutdown();
    slowFirstTask.get();

    assertThat(slowFirstTask.getStatus()).isEqualTo(Status.FINISHED);
    assertThat(fastSecondTask.getStatus()).isEqualTo(Status.NOT_FINISHED);
  }

  @Test
  @DisplayName("Throws IllegalStateException in invoke after shutdown")
  void testInvokeAfterShutdown() {
    ThreadPool savedPool = createThreadPool(4);

    useThreadPool(savedPool, pool -> {
      pool.invoke(() -> 1);
      pool.invoke(() -> 2);
      pool.invoke(() -> 4);
    });

    assertThatThrownBy(() -> savedPool.invoke(() -> 8))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("Shutdown stops ThreadPool even when executed task catches InterruptedException")
  void testShutdownInterruptedException() {
    ThreadPool pool = createThreadPool(5);

    pool.invoke(() -> {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException ignored) {
      }
      return null;
    });

    pool.shutdown();
    sleepForMillis(500);

    assertThat(pool.getThreads()).noneMatch(Thread::isAlive);
  }

  @Test
  @DisplayName("Shutdown stops ThreadPool even when executed task catches InterruptedException (await)")
  void testShutdownInterruptedExceptionAwait() throws InterruptedException {
    ThreadPool pool = createThreadPool(5);

    pool.invoke(() -> {
      try {
        Thread.sleep(5000);
      } catch (InterruptedException ignored) {
      }
      return null;
    });

    pool.shutdown();
    pool.awaitFullShutdown();

    assertThat(pool.getThreads()).noneMatch(Thread::isAlive);
  }

  @Test
  @DisplayName("Manual composition of two tasks")
  void testManualCompose() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> task1 = pool.invoke(() -> {
        sleepForMillis(500);
        return 13;
      });

      ComposableFuture<Integer> task2 = pool.invoke(() -> {
        sleepForMillis(500);
        return 37;
      });

      ComposableFuture<Integer> combiningTask = pool.invoke(() -> {
        try {
          return task1.get() + task2.get();
        } catch (ExecutionException | InterruptedException e) {
          throw new RuntimeException(e);
        }
      });

      assertThat(getValue(combiningTask)).isEqualTo(13 + 37);
    });
  }

  @Test
  @DisplayName("Composition via thenApply")
  void testThenApply() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> parentTask = pool.invoke(() -> {
        sleepForMillis(500);
        return 13;
      });

      ComposableFuture<Integer> childTask = parentTask.thenApply(value -> value + 37);

      assertThat(getValue(childTask)).isEqualTo(13 + 37);
    });
  }

  @Test
  @DisplayName("Composition via thenApply on finished task")
  void testThenApplyOnFinished() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> task1 = pool.invoke(() -> 13);

      getValue(task1);
      ComposableFuture<Integer> task2 = task1.thenApply(value -> value + 37);
      assertThat(getValue(task2)).isEqualTo(13 + 37);
    });
  }

  @Test
  @DisplayName("Composition via thenApply failed")
  void testThenApplyFailure() {
    useThreadPool(1, pool -> {
      ComposableFuture<Integer> task1 = pool.invoke(() -> {
        throw new RuntimeException();
      });

      ComposableFuture<Integer> task2 = task1.thenApply(value -> value + 5);
      assertThatThrownBy(task2::get).isInstanceOf(ExecutionException.class);
    });
  }

  @Test
  @DisplayName("Composition via thenApply does not block pool")
  void testThenApplyDoesNotBlock() {
    useThreadPool(2, pool -> {

      ComposableFuture<Integer> parentTask = pool.invoke(() -> {
        try {
          Thread.sleep(10000);
        } catch (InterruptedException ignored) {
        }
        return 13;
      });

      //noinspection unused
      ComposableFuture<Integer> childTaskThatShouldWait = parentTask.thenApply(value -> value + 37);
      ComposableFuture<Integer> taskThatShouldBeExecutedOnFreeThread = pool.invoke(() -> 42);

      sleepForMillis(500);

      assertThat(taskThatShouldBeExecutedOnFreeThread.isReady()).isEqualTo(true);
      assertThat(getValue(taskThatShouldBeExecutedOnFreeThread)).isEqualTo(42);
    });
  }

  @Test
  @DisplayName("Tasks queue is fair (1 thread)")
  void testTaskQueue() {
    useThreadPool(1, pool -> {
      var counter = new AtomicInteger(0);

      List<ComposableFuture<Integer>> tasks = new ArrayList<>(100);
      for (int i = 0; i < 100; ++i) {
        tasks.add(pool.invoke(counter::getAndIncrement));
      }

      for (int i = 0; i < tasks.size(); ++i) {
        var task = tasks.get(i);
        assertThat(getValue(task)).isEqualTo(i);
      }
    });
  }

  @Test
  @DisplayName("Pool use multiple threads")
  void testMultipleThreadsAreUsed() {
    useThreadPool(2, pool -> {
      //noinspection unused
      ComposableFuture<Object> taskThatWaits = pool.invoke(() -> {
        // hold a thread until test timeout
        sleepForMillis(6000);
        return null;
      });

      ComposableFuture<Integer> taskThatExecutesQuickly = pool.invoke(() -> 5);

      assertThat(getValue(taskThatExecutesQuickly)).isEqualTo(5);
    });
  }

  @RepeatedTest(1000) // repeat a lot of time to cover different executions
  @DisplayName("thenApply task is definitely not lost")
  void testThenApplyTaskIsNotAccidentallyLost() {
    // 4 threads seems enough to trigger this problem
    useThreadPool(4, pool -> {
      ComposableFuture<Integer> parentTask = pool.invoke(() -> 13);

      // we call thenApply on another thread to make sure that it's not invoked immediately
      ComposableFuture<ComposableFuture<Integer>> createThenApplyTask =
          pool.invoke(() -> parentTask.thenApply(value -> value + 37));

      ComposableFuture<Integer> childTask = getValue(createThenApplyTask);

      assertThat(getValue(childTask)).isEqualTo(13 + 37);
    });
  }
}
