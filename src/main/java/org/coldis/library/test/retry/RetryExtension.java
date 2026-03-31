package org.coldis.library.test.retry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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
 * Spring's before/after test method callbacks and also manually invokes JUnit's
 * {@code @BeforeEach} and {@code @AfterEach} methods on each attempt. If
 * {@link FailFastExtension} has flagged a previous failure (fail-fast enabled),
 * remaining attempts are skipped.
 * </p>
 *
 * <h2>Configuration via system properties</h2> The following system properties
 * can be provided (e.g., via Maven/Gradle or JVM args) to tune the behavior:
 * <ul>
 * <li>project.config.source.test.retry-and-fail-fast.max-attempts — default:
 * 3</li>
 * <li>project.config.source.test.retry-and-fail-fast.fixed-delay-before-next-attempt
 * — default: 1000 ms</li>
 * <li>project.config.source.test.retry-and-fail-fast.random-delay-before-next-attempt
 * — default: 10000 ms</li>
 * </ul>
 * The actual delay before attempt N is: (fixedDelay + random[0..randomDelay]) *
 * N.
 *
 * <h2>How to use</h2> Prefer using the meta-annotation
 * {@link org.coldis.library.test.retry.TestWithRetry} on your test classes:
 *
 * <pre>
 * {@code
 * &#64;TestWithRetry
 * class MyFlakyTests {
 *   &#64;Test
 *   void shouldEventuallyPass() { ... }
 * }
 * }
 * </pre>
 *
 * You may also combine retry with fail-fast using
 * {@link org.coldis.library.test.TestWithRetryAndFailFast}.
 *
 * Alternatively, you can directly register the extension:
 *
 * <pre>
 * {@code
 * &#64;ExtendWith(RetryExtension.class)
 * class MyTests { ... }
 * }
 * </pre>
 */
public class RetryExtension implements TestExecutionExceptionHandler, TestWatcher {
	private static final Log LOGGER = LogFactory.getLog(RetryExtension.class);

	/** Default maximum attempts if none is configured. */
	private static final int MAX_ATTEMPTS = 3;

	/** Default fixed delay (ms) added before the next attempt. */
	public static final Integer FIXED_DELAY_BEFORE_NEXT_ATTEMPT = 500;

	/** Default random delay (ms upper bound) added before the next attempt. */
	public static final Integer RANDOM_DELAY_BEFORE_NEXT_ATTEMPT = 3_000;

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
	 * @param  attempt attempt number (1-based)
	 * @return         delay in milliseconds before the next attempt
	 */
	public static Long getDelayBeforeNextAttempt(
			final Integer attempt) {
		return (Integer
				.parseInt(System.getProperty("project.config.source.test.retry-and-fail-fast.fixed-delay-before-next-attempt",
						String.valueOf(RetryExtension.FIXED_DELAY_BEFORE_NEXT_ATTEMPT)))
				+ RandomHelper.getPositiveRandomLong(
						Long.parseLong(System.getProperty("project.config.source.test.retry-and-fail-fast.random-delay-before-next-attempt",
								String.valueOf(RetryExtension.RANDOM_DELAY_BEFORE_NEXT_ATTEMPT)))))
				* (attempt - 1);
	}

	/**
	 * Collects all methods annotated with the given annotation from the class
	 * hierarchy (subclass first, walking up to Object).
	 */
	private List<Method> getAnnotatedMethods(
			final Class<?> testClass,
			final Class<? extends java.lang.annotation.Annotation> annotation) {
		final List<Method> methods = new ArrayList<>();
		Class<?> currentClass = testClass;
		while (currentClass != null && currentClass != Object.class) {
			for (final Method method : currentClass.getDeclaredMethods()) {
				if (method.isAnnotationPresent(annotation)) {
					// Skips methods that are overridden by a subclass (already collected).
					final boolean overriddenBySubclass = methods.stream()
							.anyMatch(collected -> collected.getName().equals(method.getName())
									&& java.util.Arrays.equals(collected.getParameterTypes(), method.getParameterTypes()));
					if (!overriddenBySubclass) {
						method.setAccessible(true);
						methods.add(method);
					}
				}
			}
			currentClass = currentClass.getSuperclass();
		}
		return methods;
	}

	private Throwable getOriginalError(
			final Throwable error) {
		Throwable originalError = error;
		// Gets the actual error, if it is an InvocationTargetException and the cause is
		// not null,
		if ((error instanceof InvocationTargetException) && (error.getCause() != null) && (error.getStackTrace().length >= 3)
				&& error.getStackTrace()[2].getClassName().equals(RetryExtension.class.getName())) {
			// Get the original exception
			originalError = error.getCause();
		}

		return originalError;
	}

	/**
	 * Core retry logic. Intercepts a thrown exception from a test method execution,
	 * then re-executes the test method up to {@link #getMaxAttempts()} times,
	 * unless fail-fast has already been triggered. Between attempts, waits for the
	 * configured backoff.
	 *
	 * The method also ensures Spring TestContext callbacks are properly invoked
	 * around each attempt and that any {@code @BeforeEach/@AfterEach} methods are
	 * executed on the test instance.
	 *
	 * @param  context   JUnit extension context
	 * @param  throwable the original test failure
	 * @throws Throwable rethrows the original throwable if all attempts fail
	 */
	@Override
	public void handleTestExecutionException(
			final ExtensionContext context,
			final Throwable throwable) throws Throwable {

		// Retries the test method up to a maximum number of attempts.
		final TestContextManager testContextManager = new TestContextManager(context.getRequiredTestClass());

		// Retries the test method up to the maximum number of attempts,
		Throwable actualThrowable = throwable;
		for (int attempt = 2; (attempt <= RetryExtension.getMaxAttempts()) && !FailFastExtension.hasFailed(); attempt++) {
			RetryExtension.LOGGER.info("Running attempt " + attempt + " of " + RetryExtension.getMaxAttempts() + " for "
					+ context.getRequiredTestMethod().getDeclaringClass().getName() + "." + context.getRequiredTestMethod().getName() + ". Error was: "
					+ actualThrowable.getClass() + "-" + actualThrowable.getMessage());

			// Waits before the next attempt.
			try {
				final Long delayBeforeNextAttempt = RetryExtension.getDelayBeforeNextAttempt(attempt);
				RetryExtension.LOGGER.warn("Waiting " + delayBeforeNextAttempt + " ms before next attempt...");
				Thread.sleep(delayBeforeNextAttempt);
			}
			catch (final InterruptedException exception) {
				RetryExtension.LOGGER.error("Error sleeping before next attempt: " + exception.getMessage(), exception);
			}

			// Runs @BeforeEach, the test method, and @AfterEach — matching JUnit's lifecycle contract.
			Throwable attemptError = null;
			try {
				// Runs Spring before-test-method callbacks.
				testContextManager.beforeTestMethod(context.getRequiredTestInstance(), context.getRequiredTestMethod());

				// Runs @BeforeEach methods (superclass first, matching JUnit's contract — stops on first failure).
				final List<Method> beforeEachMethods = this.getAnnotatedMethods(context.getRequiredTestInstance().getClass(), org.junit.jupiter.api.BeforeEach.class);
				RetryExtension.LOGGER.info("Found " + beforeEachMethods.size() + " @BeforeEach methods for " + context.getRequiredTestInstance().getClass().getName());
				for (int methodIndex = beforeEachMethods.size() - 1; methodIndex >= 0; methodIndex--) {
					final Method method = beforeEachMethods.get(methodIndex);
					RetryExtension.LOGGER.info("Running @BeforeEach: " + method.getDeclaringClass().getName() + "." + method.getName());
					method.invoke(context.getRequiredTestInstance());
				}

				// Runs the test method.
				context.getRequiredTestMethod().invoke(context.getRequiredTestInstance());

			}
			// Catches any error from @BeforeEach or the test method.
			catch (final Throwable error) {
				attemptError = this.getOriginalError(error);
			}
			// Always run @AfterEach and Spring after-test-method callbacks.
			finally {
				// Runs all @AfterEach methods (subclass first) — all must run even if one fails.
				final List<Method> afterEachMethods = this.getAnnotatedMethods(context.getRequiredTestInstance().getClass(), org.junit.jupiter.api.AfterEach.class);
				RetryExtension.LOGGER.info("Found " + afterEachMethods.size() + " @AfterEach methods for " + context.getRequiredTestInstance().getClass().getName());
				for (final Method method : afterEachMethods) {
					try {
						RetryExtension.LOGGER.info("Running @AfterEach: " + method.getDeclaringClass().getName() + "." + method.getName());
						method.invoke(context.getRequiredTestInstance());
					}
					catch (final Throwable error) {
						RetryExtension.LOGGER.error("Error running @AfterEach " + method.getDeclaringClass().getName() + "." + method.getName() + ": " + error.getMessage(), error);
						if (attemptError == null) {
							attemptError = this.getOriginalError(error);
						}
					}
				}
				try {
					testContextManager.afterTestMethod(context.getRequiredTestInstance(), context.getRequiredTestMethod(), attemptError);
				}
				catch (final Throwable error) {
					RetryExtension.LOGGER.error("Error finishing test context manager for " + context.getRequiredTestMethod().getDeclaringClass().getName()
							+ "." + context.getRequiredTestMethod().getName(), error);
				}
			}

			// If the attempt succeeded (including @AfterEach), exit.
			if (attemptError == null) {
				return;
			}
			actualThrowable = attemptError;

		}

		// If the test method failed after all attempts throw the exception.
		throw actualThrowable;
	}

}
