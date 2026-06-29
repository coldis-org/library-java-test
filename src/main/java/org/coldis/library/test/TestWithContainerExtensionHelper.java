package org.coldis.library.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Container extension.
 */
public class TestWithContainerExtensionHelper {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(TestWithContainerExtensionHelper.class);

	/**
	 * Gets the containers from tests.
	 *
	 * @param  context Test context.
	 * @return
	 */
	public static Collection<Field> getContainersFieldsFromTests(
			final ExtensionContext context) {
		final Collection<Field> containersFields = new ArrayList<>();
		for (final Field field : FieldUtils.getAllFieldsList(context.getTestClass().get())) {
			if (!Objects.equals(TestHelper.class, field.getDeclaringClass())) {
				if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
					if (field.getType().equals(GenericContainer.class)) {
						containersFields.add(field);
					}
				}
			}
		}
		return containersFields;
	}

	/**
	 * Starts the test container.
	 *
	 * @param field Container field.
	 */
	public static void startTestContainer(
			final Class<?> testClass,
			final Field field) {
		try {
			TestWithContainerExtensionHelper.LOGGER.info("Test container '{}' starting for class '{}'.", field.getName(), testClass.getSimpleName());
			GenericContainer<?> container = (GenericContainer<?>) field.get(null);
			if (TestWithContainerExtensionHelper.shouldReuseTestContainers(testClass)) {
				container.withReuse(true);
			}
			try {
				container.start();
				// Verify the container is actually running after start() — a killed
				// container may hold stale state causing start() to succeed silently.
				if (!container.isRunning()) {
					throw new IllegalStateException("Container '" + field.getName() + "' is not running after start().");
				}
			}
			catch (final Exception startException) {
				// Container was previously stopped and cannot be restarted — recreate it.
				TestWithContainerExtensionHelper.LOGGER.info("Test container '{}' failed to start, recreating for class '{}'.", field.getName(),
						testClass.getSimpleName());
				container = TestWithContainerExtensionHelper.recreateContainer(testClass, field);
				if (TestWithContainerExtensionHelper.shouldReuseTestContainers(testClass)) {
					container.withReuse(true);
				}
				container.start();
			}
			// Re-read the field to get the final (possibly recreated) container reference.
			final GenericContainer<?> startedContainer = (GenericContainer<?>) field.get(null);
			// Sets the container ports as system properties.
			startedContainer.getExposedPorts().forEach((
					exposedPort) -> {
				final Integer mappedPort = startedContainer.getMappedPort(exposedPort);
				final String mappedPortPropertyName = field.getName() + "_" + exposedPort;
				System.setProperty(mappedPortPropertyName, mappedPort.toString());
				TestWithContainerExtensionHelper.LOGGER.info("Test container '{}' for class '{}' setting {}={}", field.getName(), testClass.getSimpleName(),
						mappedPortPropertyName, mappedPort.toString());
			});
			// Sets the container host as system property.
			final String containerIpAddressEnv = field.getName() + "_IP";
			final String containerIpAddress = startedContainer.getContainerInfo().getNetworkSettings().getIpAddress();
			System.setProperty(containerIpAddressEnv, containerIpAddress);
			TestWithContainerExtensionHelper.LOGGER.info("Test container '{}' for class '{}' setting {}={}", field.getName(), testClass.getSimpleName(),
					containerIpAddressEnv, containerIpAddress);
		}
		catch (final Exception exception) {
			TestWithContainerExtensionHelper.LOGGER.error("Test container '{}' did not start for class '{}': {}.", field.getName(), testClass.getSimpleName(),
					exception.getLocalizedMessage());
			TestWithContainerExtensionHelper.LOGGER.debug("Error starting container.", exception);
		}
	}

	/**
	 * Recreates a container by finding and invoking the corresponding factory
	 * method on TestHelper, then updates both the TestHelper field and the
	 * test class field with the new instance.
	 *
	 * @param  testClass Test class.
	 * @param  field     Container field on the test class.
	 * @return           The new container instance.
	 */
	@SuppressWarnings("unchecked")
	private static GenericContainer<?> recreateContainer(
			final Class<?> testClass,
			final Field field) throws Exception {
		// Find the matching field on TestHelper.
		final Field helperField = FieldUtils.getField(TestHelper.class, field.getName(), true);
		if (helperField != null) {
			// Derive the factory method name from the field name (e.g. POSTGRES_CONTAINER -> createPostgresContainer).
			final String[] parts = field.getName().split("_");
			if (parts.length >= 1) {
				final String methodName = "create" + parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1).toLowerCase() + "Container";
				final java.lang.reflect.Method factoryMethod = TestHelper.class.getMethod(methodName);
				final GenericContainer<?> newContainer = (GenericContainer<?>) factoryMethod.invoke(null);
				// Update both TestHelper and the test class field.
				FieldUtils.writeStaticField(helperField, newContainer, true);
				if (!Objects.equals(field.getDeclaringClass(), TestHelper.class)) {
					FieldUtils.writeStaticField(field, newContainer, true);
				}
				TestWithContainerExtensionHelper.LOGGER.info("Test container '{}' recreated for class '{}'.", field.getName(), testClass.getSimpleName());
				return newContainer;
			}
		}
		throw new IllegalStateException("Cannot recreate container for field: " + field.getName());
	}

	/**
	 * If test containers should be started in parallel.
	 *
	 * @param  testClass Test class.
	 * @return           If test containers should be started in parallel.
	 */
	public static Boolean shouldStartTestContainersInParallel(
			final Class<?> testClass) {
		return (testClass.getAnnotation(TestWithContainer.class) != null) && testClass.getAnnotation(TestWithContainer.class).parallel();
	}

	/**
	 * If test containers should be reused.
	 *
	 * @param  testClass Test class.
	 * @return           If test containers should be reused.
	 */
	public static Boolean shouldReuseTestContainers(
			final Class<?> testClass) {
		return (testClass.getAnnotation(TestWithContainer.class) != null) && testClass.getAnnotation(TestWithContainer.class).reuse();
	}

	/**
	 * Stops the test container.
	 *
	 * @param field Container field.
	 */
	public static void stopTestContainer(
			final Field field) {
		try {
			final GenericContainer<?> container = (GenericContainer<?>) field.get(null);
			container.stop();
			container.close();
		}
		catch (final Exception exception) {
			TestWithContainerExtensionHelper.LOGGER.error("Error stopping container.", exception);
		}
	}

}
