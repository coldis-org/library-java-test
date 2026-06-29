package org.coldis.library.test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Container extension. Starts each container once and keeps it running for the whole JVM run
 * (stopped only at the end, via the global store), so its address stays stable. Containers must
 * not be stopped/restarted between test classes: cached Spring contexts would keep pointing at the
 * old address and fail to reconnect (AMQ219013/219007, "context has been closed").
 */
@Order(Integer.MIN_VALUE)
public class StartTestWithContainerExtension implements BeforeAllCallback {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(StartTestWithContainerExtension.class);

	/** Single thread executor. */
	private final Executor singleThreadExecutor = Executors.newSingleThreadExecutor();

	/** Multi thread executor. */
	private final Executor multiThreadExecutor = Executors.newWorkStealingPool();

	/**
	 * Before all tests.
	 *
	 * @param  context   Test context.
	 * @throws Exception If the test fails.
	 */
	@Override
	public void beforeAll(
			final ExtensionContext context) throws Exception {

		final Class<?> testClass = context.getTestClass().orElseThrow();
		final Collection<Field> containersFields = TestWithContainerExtensionHelper.getContainersFieldsFromTests(context);
		final Executor executor = TestWithContainerExtensionHelper.shouldStartTestContainersInParallel(testClass) ? this.multiThreadExecutor
				: this.singleThreadExecutor;

		// Starts containers (if not already started by a previous class).
		@SuppressWarnings("unchecked")
		final CompletableFuture<Void>[] containersStartJobs = containersFields.stream().map(field -> CompletableFuture.runAsync((() -> {
			try {
				final GenericContainer<?> container = (GenericContainer<?>) field.get(null);
				if (!container.isRunning()) {
					TestWithContainerExtensionHelper.startTestContainer(testClass, field);
				}
			}
			catch (final Exception exception) {
				throw new RuntimeException(exception);
			}
		}), executor)).toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(containersStartJobs).get();

		// Registers a single stop per container in the global store (runs at the end of the whole run).
		containersFields.stream().map(field -> {
			try {
				return (GenericContainer<?>) field.get(null);
			}
			catch (final Exception exception) {
				throw new RuntimeException(exception);
			}
		}).forEach(container -> context.getRoot().getStore(Namespace.GLOBAL).getOrComputeIfAbsent("container-" + container.hashCode(),
				key -> (CloseableResource) container::stop, CloseableResource.class));
		StartTestWithContainerExtension.LOGGER.debug("Test containers started and registered for run-lifetime stop.");
	}

}
