package org.coldis.library.test.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Meta-annotation to enable {@link RetryExtension} on a test class.
 * <p>
 * Apply this annotation to make test methods automatically retried on failure
 * according to the configured system properties. See
 * {@link RetryExtension} for details and configuration keys.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>
 * {@code
 * @TestWithRetry
 * class MyFlakyTests { ... }
 * }
 * </pre>
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RetryExtension.class)
public @interface TestWithRetry {}
