package org.coldis.library.test.failfast;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.junit.jupiter.DisabledIf;

/**
 * Meta-annotation to enable {@link FailFastExtension} on a test class.
 * <p>
 * When applied, tests will be skipped after the first failure recorded within
 * the same run (when fail-fast is enabled). See {@link FailFastExtension} for
 * configuration.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>
 * {@code
 * @TestWithFailFast
 * class MySuite { ... }
 * }
 * </pre>
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(FailFastExtension.class)
@DisabledIf(expression = "#{T(org.coldis.library.test.failfast.FailFastExtension).hasFailed()}")
public @interface TestWithFailFast {}
