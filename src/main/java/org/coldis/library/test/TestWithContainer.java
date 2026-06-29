package org.coldis.library.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.annotation.DirtiesContext;

/**
 * Test with container. Each test class gets a fresh Spring context, dirtied after the class, so it
 * never reuses connections bound to a container address from a previous class.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public @interface TestWithContainer {

	/**
	 * Whether containers should start in parallel.
	 *
	 * @return if the containers should start in parallel.
	 */
	boolean parallel() default true;

	/**
	 * Whether to reuse containers.
	 *
	 * @return if the containers should be reused.
	 */
	boolean reuse() default false;

}
