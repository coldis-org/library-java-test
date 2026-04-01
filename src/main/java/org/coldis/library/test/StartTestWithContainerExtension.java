package org.coldis.library.test;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;

/**
 * Container extension.
 */
@Order(Integer.MIN_VALUE)
public class StartTestWithContainerExtension implements BeforeAllCallback, BeforeEachCallback {

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

		// Acquires references first to prevent delayed stop threads from stopping containers.
		final long stopDelay = TestWithContainerExtensionHelper.getStopDelay(testClass);
		containersFields.forEach(field -> TestWithContainerExtensionHelper.acquireContainer(field.getName()));

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
		containersFields.forEach(field -> {
			final String containerKey = field.getName();
			context.getStore(Namespace.create(testClass)).getOrComputeIfAbsent("container-" + containerKey, key -> {
				return (CloseableResource) () -> {
					TestWithContainerExtensionHelper.releaseContainer(containerKey);
					// Re-read the field to get the current (possibly recreated) container.
					final GenericContainer<?> container;
					try {
						container = (GenericContainer<?>) field.get(null);
					}
					catch (final Exception exception) {
						throw new RuntimeException(exception);
					}
					if (stopDelay > 0) {
						TestWithContainerExtensionHelper.scheduleDelayedStop(containerKey, container, stopDelay);
					}
					else {
						final java.util.concurrent.atomic.AtomicInteger refCount =
								TestWithContainerExtensionHelper.getRefCount(containerKey);
						if (refCount == null || refCount.get() <= 0) {
							container.stop();
						}
					}
				};
			}, CloseableResource.class);
		});

	}

	/**
	 * Before each test, verifies that all containers are still running.
	 * If any container is down, restarts it and marks the Spring application
	 * context dirty so that connections are rebuilt with the new port mappings.
	 */
	@Override
	public void beforeEach(
			final ExtensionContext context) throws Exception {

		final Class<?> testClass = context.getTestClass().orElseThrow();
		final Collection<Field> containersFields = TestWithContainerExtensionHelper.getContainersFieldsFromTests(context);

		boolean anyRestarted = false;
		for (final Field field : containersFields) {
			final GenericContainer<?> container = (GenericContainer<?>) field.get(null);
			if (!container.isRunning()) {
				StartTestWithContainerExtension.LOGGER.warn("Container '{}' is not running before test '{}'. Restarting.",
						field.getName(), context.getDisplayName());
				TestWithContainerExtensionHelper.startTestContainer(testClass, field);
				anyRestarted = true;
			}
		}

		// If any container was restarted, mark the Spring context dirty so that
		// connections (DataSource, Redis, Artemis) are rebuilt with the new ports.
		if (anyRestarted) {
			try {
				final ExtensionContext.Store store = context.getRoot().getStore(Namespace.create(SpringExtension.class));
				final TestContextManager testContextManager = store.get(testClass, TestContextManager.class);
				if (testContextManager != null) {
					StartTestWithContainerExtension.LOGGER.info("Marking Spring application context dirty after container restart.");
					testContextManager.getTestContext().markApplicationContextDirty(
							org.springframework.test.annotation.DirtiesContext.HierarchyMode.EXHAUSTIVE);
				}
			}
			catch (final Exception exception) {
				StartTestWithContainerExtension.LOGGER.warn("Could not mark Spring context dirty after container restart: {}",
						exception.getMessage());
			}
		}
	}

}
