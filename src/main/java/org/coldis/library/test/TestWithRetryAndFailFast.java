package org.coldis.library.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.coldis.library.test.failfast.TestWithFailFast;
import org.coldis.library.test.retry.TestWithRetry;

/**
 * Convenience meta-annotation that applies both {@link TestWithRetry} and
 * {@link TestWithFailFast} to a test class.
 * <p>
 * Use this when you want flaky tests to be retried automatically, but stop the
 * entire test run early once any test ultimately fails.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>
 * {@code
 * @TestWithRetryAndFailFast
 * class MyIntegrationTests { ... }
 * }
 * </pre>
 */
@Inherited
@TestWithRetry
@TestWithFailFast
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestWithRetryAndFailFast {}
