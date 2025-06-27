package org.coldis.library.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

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
		for (final Field field : context.getTestClass().get().getFields()) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
				if (field.getType().equals(GenericContainer.class)) {
					containersFields.add(field);
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
			final GenericContainer<?> container = (GenericContainer<?>) field.get(null);
			container.start();
			// Sets the container ports as system properties.
			container.getExposedPorts().forEach((
					exposedPort) -> {
				final Integer mappedPort = container.getMappedPort(exposedPort);
				final String mappedPortPropertyName = field.getName() + "_" + exposedPort;
				System.setProperty(mappedPortPropertyName, mappedPort.toString());
			});
			// Sets the container host as system property.
			final String containerIpAddressEnv = field.getName() + "_IP";
			final String containerIpAddress = container.getContainerInfo().getNetworkSettings().getIpAddress();
			System.setProperty(containerIpAddressEnv, containerIpAddress);
			TestWithContainerExtensionHelper.LOGGER.info("Test container '{}' started for class '{}' with {}={}", field.getName(), testClass.getSimpleName(),
					containerIpAddressEnv, containerIpAddress);
		}
		catch (final Exception exception) {
			TestWithContainerExtensionHelper.LOGGER.error("Test container '{}' did not start for class '{}': {}.", field.getName(), testClass.getSimpleName(),
					exception.getLocalizedMessage());
			TestWithContainerExtensionHelper.LOGGER.debug("Error starting container.", exception);
		}
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
