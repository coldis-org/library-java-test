package org.coldis.library.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.test.annotation.DirtiesContext;

/**
 * Test with container.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TestWithContainer {

	/**
	 * Whether containers should start in parallel. Defaults to {@code true}.
	 *
	 * @return if the containers should start in parallel.
	 */
	boolean parallel() default true;
	
	/**
	 * Whether to reuse containers. Defaults to {@code true}.
	 */
	boolean reuse() default true;

	/**
	 * Seconds to wait before stopping a container after the last test class
	 * releases it. Gives the next test class time to acquire it. Defaults to 300.
	 * A test JVM usually caches its Spring context with the containers' mapped
	 * ports; if a container stops while another test class runs (e.g. one that
	 * starts its own slow container) and is started again, it gets new ports and
	 * the cached context keeps the old ones, so every later class in that JVM
	 * fails. Containers are still removed when the JVM exits.
	 */
	long stopDelay() default 300;

}
