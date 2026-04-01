package org.coldis.library.test.test;

import org.coldis.library.test.StartTestWithContainerExtension;
import org.coldis.library.test.TestHelper;
import org.coldis.library.test.TestWithContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;

/**
 * Verifies that {@link StartTestWithContainerExtension}'s {@code beforeEach}
 * callback detects a stopped container and restarts it before the next test.
 *
 * <p>The test is ordered so that the first method kills Redis and the second
 * method asserts it was automatically restarted by the extension.</p>
 */
@TestWithContainer(parallel = false)
@ExtendWith(StartTestWithContainerExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ContainerRestartOnFailureTest {

	public static GenericContainer<?> REDIS_CONTAINER = TestHelper.REDIS_CONTAINER;

	/**
	 * Kills the Redis container to simulate an unexpected crash.
	 * The container should be detected as down and restarted by the
	 * extension's {@code beforeEach} before the next test runs.
	 */
	@Test
	@Order(1)
	public void testKillContainer() throws Exception {
		Assertions.assertTrue(REDIS_CONTAINER.isRunning(), "Redis should be running before kill");

		// Forcefully stop the container to simulate a crash.
		REDIS_CONTAINER.getDockerClient()
				.killContainerCmd(REDIS_CONTAINER.getContainerId())
				.exec();

		// Wait briefly for Docker to register the container as stopped.
		Thread.sleep(1000);
		Assertions.assertFalse(REDIS_CONTAINER.isRunning(), "Redis should be stopped after kill");
	}

	/**
	 * By the time this test runs, {@code beforeEach} should have detected
	 * the stopped container and restarted it.
	 */
	@Test
	@Order(2)
	public void testContainerWasRestarted() {
		Assertions.assertTrue(REDIS_CONTAINER.isRunning(), "Redis should have been restarted by beforeEach");
		Assertions.assertNotNull(System.getProperty("REDIS_CONTAINER_6379"),
				"Redis mapped port system property should be set after restart");
	}

}
