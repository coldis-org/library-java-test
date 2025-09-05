package org.coldis.library.test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Container extension.
 */
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
		final Class<?> testClass = context.getTestClass().orElseThrow();
		final Collection<Field> containersFieldsFromTests = TestWithContainerExtensionHelper.getContainersFieldsFromTests(context);
		final Executor executor = TestWithContainerExtensionHelper.shouldStartTestContainersInParallel(testClass) ? Executors.newWorkStealingPool()
				: Executors.newSingleThreadExecutor();
		@SuppressWarnings("unchecked")
		final CompletableFuture<Void>[] containersFieldsJobs = containersFieldsFromTests.stream().map(field -> (CompletableFuture.runAsync(() -> {
			try {
				final GenericContainer<?> container = (GenericContainer<?>) field.get(null);
				StartTestWithContainerExtension.CONTAINER_LOCK.computeIfAbsent(container, k -> 0);
				StartTestWithContainerExtension.CONTAINER_LOCK.put(container, StartTestWithContainerExtension.CONTAINER_LOCK.get(container) - 1);
				final Boolean shouldReuseContainer = TestWithContainerExtensionHelper.shouldReuseTestContainers(testClass);
				if (container.isRunning()) {
					if (shouldReuseContainer) {
						CompletableFuture.runAsync(() -> {
							try {
								Thread.sleep(20_000);
								if (StartTestWithContainerExtension.CONTAINER_LOCK.getOrDefault(container, 0) <= 0) {
									StopTestWithContainerExtension.LOGGER.info("Reused test container '{}' stopping for class '{}'.", field.getName(),
											testClass.getSimpleName());
									TestWithContainerExtensionHelper.stopTestContainer(field);
								}
								else {
									StopTestWithContainerExtension.LOGGER.info("Reused test container '{}' not stopping for class '{}' as it is still in use.",
											field.getName(), testClass.getSimpleName());
								}
							}
							catch (final Exception exception) {
								throw new RuntimeException(exception);
							}
						}, Executors.newSingleThreadExecutor());
					}
					else {
						TestWithContainerExtensionHelper.stopTestContainer(field);
					}
				}
			}
			catch (final Exception exception) {
				throw new RuntimeException(exception);
			}
		}, executor))).toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(containersFieldsJobs).get();
	}

}
