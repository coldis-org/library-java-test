package org.coldis.library.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container extension.
 */
@Deprecated
public class StopTestWithContainerExtension implements AfterAllCallback {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(StopTestWithContainerExtension.class);

	/**
	 * After each test.
	 *
	 * @param  context   Test context.
	 * @throws Exception If the test fails.
	 */
	@Override
	public void afterAll(
			final ExtensionContext context) throws Exception {
	}

}
