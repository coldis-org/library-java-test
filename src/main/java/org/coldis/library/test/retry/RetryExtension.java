package org.coldis.library.test.retry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.coldis.library.helper.RandomHelper;
import org.coldis.library.test.failfast.FailFastExtension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.test.context.TestContextManager;

/**
 * JUnit 5 extension that automatically retries a failing test method a
 * configurable number of times, with a configurable backoff between attempts.
 * <p>
 * It integrates with Spring's {@link TestContextManager} to properly run
 * Spring's before/after test method callbacks and also manually invokes
 * JUnit's {@code @BeforeEach} and {@code @AfterEach} methods on each attempt.
 * If {@link FailFastExtension} has flagged a previous failure (fail-fast
 * enabled), remaining attempts are skipped.
 * </p>
 *
 * <h2>Configuration via system properties</h2>
 * The following system properties can be provided (e.g., via Maven/Gradle or
 * JVM args) to tune the behavior:
 * <ul>
 *   <li>project.config.source.test.retry-and-fail-fast.max-attempts — default: 3</li>
 *   <li>project.config.source.test.retry-and-fail-fast.fixed-delay-before-next-attempt — default: 1000 ms</li>
 *   <li>project.config.source.test.retry-and-fail-fast.random-delay-before-next-attempt — default: 10000 ms</li>
 * </ul>
 * The actual delay before attempt N is: (fixedDelay + random[0..randomDelay]) * N.
 *
 * <h2>How to use</h2>
 * Prefer using the meta-annotation {@link org.coldis.library.test.retry.TestWithRetry}
 * on your test classes:
 * <pre>
 * {@code
 * @TestWithRetry
 * class MyFlakyTests {
 *   @Test
 *   void shouldEventuallyPass() { ... }
 * }
 * }
 * </pre>
 * You may also combine retry with fail-fast using
 * {@link org.coldis.library.test.TestWithRetryAndFailFast}.
 *
 * Alternatively, you can directly register the extension:
 * <pre>
 * {@code
 * @ExtendWith(RetryExtension.class)
 * class MyTests { ... }
 * }
 * </pre>
 */
public class RetryExtension implements TestExecutionExceptionHandler, TestWatcher {
  private static final Log LOGGER = LogFactory.getLog(RetryExtension.class);

  /** Default maximum attempts if none is configured. */
  private static final int MAX_ATTEMPTS = 3;

  /** Default fixed delay (ms) added before the next attempt. */
  public static final Integer FIXED_DELAY_BEFORE_NEXT_ATTEMPT = 1000;

  /** Default random delay (ms upper bound) added before the next attempt. */
  public static final Integer RANDOM_DELAY_BEFORE_NEXT_ATTEMPT = 10000;

  /**
   * @return The configured maximum number of attempts to run a test method before
   *         letting it fail.
   */
  public static int getMaxAttempts() {
    return Integer.parseInt(System.getProperty("project.config.source.test.retry-and-fail-fast.max-attempts", String.valueOf(RetryExtension.MAX_ATTEMPTS)));
  }

  /**
   * Computes the delay before the next attempt, multiplying base delay by the
   * attempt index for a simple linear backoff.
   *
   * @param attempt attempt number (1-based)
   * @return delay in milliseconds before the next attempt
   */
  public static Long getDelayBeforeNextAttempt(
    final Integer attempt
  ) {
    return (Integer.parseInt(System.getProperty("project.config.source.test.retry-and-fail-fast.fixed-delay-before-next-attempt", String.valueOf(RetryExtension.FIXED_DELAY_BEFORE_NEXT_ATTEMPT)))
            + RandomHelper.getPositiveRandomLong(Long.parseLong(System.getProperty("project.config.source.test.retry-and-fail-fast.random-delay-before-next-attempt", String.valueOf(RetryExtension.RANDOM_DELAY_BEFORE_NEXT_ATTEMPT)))))
           * attempt;
  }

  /**
   * Core retry logic. Intercepts a thrown exception from a test method
   * execution, then re-executes the test method up to {@link #getMaxAttempts()}
   * times, unless fail-fast has already been triggered. Between attempts, waits
   * for the configured backoff.
   *
   * The method also ensures Spring TestContext callbacks are properly invoked
   * around each attempt and that any {@code @BeforeEach/@AfterEach} methods are
   * executed on the test instance.
   *
   * @param context   JUnit extension context
   * @param throwable the original test failure
   * @throws Throwable rethrows the original throwable if all attempts fail
   */
  @Override
  public void handleTestExecutionException(final ExtensionContext context, final Throwable throwable) throws Throwable {

    // Retries the test method up to a maximum number of attempts.
    final TestContextManager testContextManager = new TestContextManager(context.getRequiredTestClass());

    for (int attempt = 1; attempt <= RetryExtension.getMaxAttempts() && !FailFastExtension.hasFailed(); attempt++) {
      try {
        // Runs before methods.
        testContextManager.beforeTestMethod(context.getRequiredTestInstance(), context.getRequiredTestMethod());

        // Runs beforeEach methods.
        final Method[] beforeEachMethods = context.getRequiredTestClass().getDeclaredMethods();

        for (final Method method : beforeEachMethods) {
          if (method.isAnnotationPresent(org.junit.jupiter.api.BeforeEach.class)) {
            method.setAccessible(true);
            method.invoke(context.getRequiredTestInstance());
          }
        }

        // Runs the test method.
        context.getRequiredTestMethod().invoke(context.getRequiredTestInstance());

        // Runs afterEach methods.
        final Method[] afterEachMethods = context.getRequiredTestClass().getDeclaredMethods();

        for (final Method method : afterEachMethods) {
          if (method.isAnnotationPresent(org.junit.jupiter.api.AfterEach.class)) {
            method.setAccessible(true);
            method.invoke(context.getRequiredTestInstance());
          }
        }

        // If the test method was executed successfully, exit the loop/method.
        return;
      } catch (Throwable error) {
        // Gets the actual error, if it is an InvocationTargetException and the cause is not null
        final Throwable originalError = this.getOriginalError(error);

        // Log the exception.
        final String testName = context.getRequiredTestMethod().getDeclaringClass().getName()
                                + "."
                                + context.getRequiredTestMethod().getName();
        RetryExtension.LOGGER.error(
          testName
          + " -- Attempt "
          + attempt
          + " of "
          + RetryExtension.getMaxAttempts()
          + " <<< FAILURE!", originalError
        );
      }
      // Finish the test context manager.
      finally {
        try {
          testContextManager.afterTestMethod(context.getRequiredTestInstance(), context.getRequiredTestMethod(), null);
        } catch (final Throwable error) {
          RetryExtension.LOGGER.error(
            "Error finishing test context manager for "
            + context.getRequiredTestMethod().getDeclaringClass().getName()
            + "."
            + context.getRequiredTestMethod().getName(), error
          );
        }
      }

      // Waits before the next attempt.
      try {
        Thread.sleep(RetryExtension.getDelayBeforeNextAttempt(attempt));
      } catch (final InterruptedException e) {
        LOGGER.error("Error sleeping before next attempt: " + e.getMessage(), e);
      }
    }

    // If the test method failed after all attempts throw the exception.
    throw throwable;
  }

  /**
   * Attempts to unwrap the original cause from reflective invocation layers for
   * clearer logging.
   *
   * @param error the caught error, possibly an InvocationTargetException
   * @return the most relevant underlying Throwable for logging
   */
  private Throwable getOriginalError(final Throwable error) {
    Throwable originalError = error;
    // Gets the actual error, if it is an InvocationTargetException and the cause is not null,
    if ((error instanceof InvocationTargetException)
        && (error.getCause() != null)
        && (error.getStackTrace().length
            >= 3)
        && error.getStackTrace()[2].getClassName().equals(RetryExtension.class.getName())) {
      // Get the original exception
      originalError = error.getCause();
    }

    return originalError;
  }
}
