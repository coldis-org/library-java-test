package org.coldis.library.test;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.collections4.CollectionUtils;
import org.coldis.library.helper.DateTimeHelper;
import org.coldis.library.helper.ReflectionHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

/**
 * Test helper.
 */
public class TestHelper {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(TestHelper.class);

	/**
	 * Very short wait time (milliseconds).
	 */
	public static final Integer VERY_SHORT_WAIT = 100;

	/**
	 * Short wait time (milliseconds).
	 */
	public static final Integer SHORT_WAIT = 500;

	/**
	 * Regular wait time (milliseconds).
	 */
	public static final Integer REGULAR_WAIT = 25 * 100;

	/**
	 * Long wait time (milliseconds).
	 */
	public static final Integer LONG_WAIT = 11 * 1000;

	/**
	 * Long wait time (milliseconds).
	 */
	public static final Integer VERY_LONG_WAIT = 29 * 1000;

	/**
	 * Regular clock.
	 */
	public static final Clock REGULAR_CLOCK = DateTimeHelper.getClock();

	/**
	 * Test user name.
	 */
	public static String TEST_USER_NAME = "test";

	/**
	 * Test user password.
	 */
	public static String TEST_USER_PASSWORD = "test";

	/**
	 * Cleans after each test.
	 */
	public static void cleanClock() {
		// Sets back to the regular clock.
		DateTimeHelper.setClock(TestHelper.REGULAR_CLOCK);
	}

	 /**
	  * Creates a Postgres container.
	  */
	 @SuppressWarnings("resource")
	 public static GenericContainer<?> createPostgresContainer() {
		  return new GenericContainer<>("coldis/infrastructure-transactional-repository:5.0.2").withExposedPorts(5432).withEnv(
						  Map.of("ENABLE_JSON_CAST", "true", "ENABLE_UNACCENT", "true", "POSTGRES_ADMIN_PASSWORD", "postgres", "POSTGRES_ADMIN_USER", "postgres",
								  "REPLICATOR_USER_NAME", "replicator", "REPLICATOR_USER_PASSWORD", "replicator", "POSTGRES_DEFAULT_USER", TestHelper.TEST_USER_NAME,
								  "POSTGRES_DEFAULT_PASSWORD", TestHelper.TEST_USER_PASSWORD, "POSTGRES_DEFAULT_DATABASE", TestHelper.TEST_USER_NAME))
				  .withCreateContainerCmdModifier(
						  (cmd) -> cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("", 5432), new ExposedPort(5432))));
	 }

	 /**
	  * Creates an Artemis container.
	  */
	 @SuppressWarnings("resource")
	 public static GenericContainer<?> createArtemisContainer() {
		  return new GenericContainer<>("coldis/infrastructure-messaging-service:2.17").withExposedPorts(8161, 61616).withEnv(
						  Map.of("ARTEMIS_USERNAME", TestHelper.TEST_USER_NAME, "ARTEMIS_PASSWORD", TestHelper.TEST_USER_PASSWORD, "ARTEMIS_PERF_JOURNAL", "ALWAYS"))
				  .withCreateContainerCmdModifier(
						  (cmd) -> cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("", 61616), new ExposedPort(61616))));
	 }

	 /**
	  * Creates a Redis container.
	  */
	 @SuppressWarnings("resource")
	 public static GenericContainer<?> createRedisContainer() {
		  return new GenericContainer<>("redis:7.2.4-bookworm").withExposedPorts(6379).withCommand("redis-server", "--save", "60", "1", "--loglevel", "warning")
				  .withCreateContainerCmdModifier(
						  (cmd) -> cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("", 6379), new ExposedPort(6379))));
	 }

	/**
	 * Waits until variable is valid.
	 *
	 * @param  <Type>             The variable type.
	 * @param  variableSupplier   Variable supplier function.
	 * @param  validVariableState The variable valid state verification.
	 * @param  maxWait            Milliseconds to wait until valid state is met.
	 * @param  poll               Milliseconds between validity verification.
	 * @param  exceptionsToIgnore Exceptions to be ignored on validity verification.
	 * @return                    If a valid variable state has been met within the
	 *                            maximum wait period.
	 * @throws Exception          If the validity verification throws a non
	 *                                ignorable exception.
	 */
	@SafeVarargs
	public static <Type> Boolean waitUntilValid(
			final Supplier<Type> variableSupplier,
			final Predicate<Type> validVariableState,
			final Integer maxWait,
			final Integer poll,
			final Class<? extends Throwable>... exceptionsToIgnore) throws Exception {
		// Valid state is not considered met by default.
		boolean validStateMet = false;
		// Validation start time stamp.
		final Long startTimestamp = System.currentTimeMillis();
		// Until wait time is not reached.
		for (Long currentTimestamp = System.currentTimeMillis(); (startTimestamp + maxWait) > currentTimestamp; currentTimestamp = System.currentTimeMillis()) {
			// If the variable state is valid.
			try {
				if (validVariableState.test(variableSupplier.get())) {
					// Valid state has been met.
					validStateMet = true;
					break;
				}
			}
			// If the variable state cannot be tested.
			catch (final Throwable throwable) {
				// If the exception is not to be ignored.
				if ((exceptionsToIgnore == null)
						|| !Arrays.asList(exceptionsToIgnore).stream().anyMatch(exception -> exception.isAssignableFrom(throwable.getClass()))) {
					// Throws the exception and stops the wait.
					throw throwable;
				}
			}
			// Waits a bit.
			Thread.sleep(poll);
		}
		// Returns if valid state has been met.
		return validStateMet;
	}

	/**
	 * Creates incomplete objects.
	 *
	 * @param  <Type>            Type.
	 * @param  baseObject        Base object to be cloned with incomplete data.
	 * @param  cloneFunction     Clone function.
	 * @param  attributesToUnset Attributes to individually unset from base object.
	 * @return                   Various clones of the base object with missing
	 *                           attributes.
	 */
	public static <Type> Collection<Type> createIncompleteObjects(
			final Type baseObject,
			final Function<Type, Type> cloneFunction,
			final Collection<String> attributesToUnset) {
		// Creates the incomplete objects list.
		final List<Type> incompleteObjects = new ArrayList<>();
		// If both base object and attributes are given.
		if ((baseObject != null) && !CollectionUtils.isEmpty(attributesToUnset)) {
			// For each attribute to unset.
			for (final String attributeToUnset : attributesToUnset) {
				// Clones the object and adds it to the list.
				final Type incompleteObject = cloneFunction.apply(baseObject);
				incompleteObjects.add(incompleteObject);
				// Sets the attribute to null.
				ReflectionHelper.setAttribute(incompleteObject, attributeToUnset, null);
			}
		}
		// Returns the incomplete objects list.
		return incompleteObjects;
	}

}
