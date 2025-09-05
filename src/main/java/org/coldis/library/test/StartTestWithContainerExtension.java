package org.coldis.library.test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Container extension.
 */
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
	 * Before each test.
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

		// Starts containers.
		@SuppressWarnings("unchecked")
		final CompletableFuture<Void>[] containersStartJobs = containersFields.stream().map(field -> (CompletableFuture.runAsync((() -> {
			// Starts the container if not already started.
			try {
				final GenericContainer<?> container = (GenericContainer<?>) field.get(null);
				if (!container.isRunning()) {
					TestWithContainerExtensionHelper.startTestContainer(testClass, field);
				}
			}
			catch (final Exception exception) {
				throw new RuntimeException(exception);
			}
		}), executor))).toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(containersStartJobs).get();

		// Registers shutdown hooks.
		containersFields.stream().map(field -> {
			try {
				return (GenericContainer<?>) field.get(null);
			}
			catch (final Exception exception) {
				throw new RuntimeException(exception);
			}
		}).forEach(container -> context.getRoot().getStore(Namespace.GLOBAL).getOrComputeIfAbsent("container-" + container.hashCode(), key -> {
			return (CloseableResource) () -> {
				container.stop();
			};
		}, CloseableResource.class));

	}

}
