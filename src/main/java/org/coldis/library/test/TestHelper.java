package org.coldis.library.test;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.coldis.library.helper.DateTimeHelper;
import org.coldis.library.helper.ReflectionHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Test helper.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
	 * Default CPU count.
	 */
	private static Long DEFAULT_CPU_QUOTA = 2L;

	/**
	 * Default memory quota.
	 */
	private static Long DEFAULT_MEMORY_QUOTA = 2L * 1024L * 1024L * 1024L;

	/**
	 * Default disk quota.
	 */
	private static Long DEFAULT_DISK_QUOTA = 10L * 1024L * 1024L * 1024L;

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
	 * Gets the test fork number.
	 *
	 * @return The test fork number.
	 */
	public static Integer getTestForkNumber() {
		final Integer forkNumber = (NumberUtils.isParsable(System.getProperty("FORK_NUMBER")) ? (Integer.parseInt(System.getProperty("FORK_NUMBER"))) : 1);
		return forkNumber;
	}

	/**
	 * Gets the test cpu quota.
	 *
	 * @return The test cpu quota.
	 */
	public static Long getCpuQuota() {
		final Long cpuQuota = (NumberUtils.isParsable(System.getProperty("CPU_QUOTA")) ? (Long.parseLong(System.getProperty("CPU_QUOTA"))) : DEFAULT_CPU_QUOTA);
		return cpuQuota;
	}

	/**
	 * Gets the test memory quota.
	 *
	 * @return The test memory quota.
	 */
	public static Long getMemoryQuota() {
		String rawMemoryQuota = System.getProperty("MEMORY_QUOTA");

		try {
			if (rawMemoryQuota != null) {
				rawMemoryQuota = rawMemoryQuota.trim().toLowerCase();
				if (rawMemoryQuota.endsWith("g")) {
					return Long.parseLong(rawMemoryQuota.substring(0, rawMemoryQuota.length() - 1)) * 1024 * 1024 * 1024;
				}
				if (rawMemoryQuota.endsWith("m")) {
					return Long.parseLong(rawMemoryQuota.substring(0, rawMemoryQuota.length() - 1)) * 1024 * 1024;
				}
			}
		}
		catch (NumberFormatException ignored) {
		}

		return DEFAULT_MEMORY_QUOTA;
	}

	/**
	 * Cleans after each test.
	 */
	public static void cleanClock() {
		// Sets back to the regular clock.
		DateTimeHelper.setClock(TestHelper.REGULAR_CLOCK);
	}

	/**
	 * Moves the clock to a specific date time.
	 *
	 * @param dateTime Date time.
	 */
	public static void moveClockTo(
			final LocalDateTime dateTime) {
		DateTimeHelper.setClock(Clock.offset(DateTimeHelper.getClock(), Duration.between(DateTimeHelper.getCurrentLocalDateTime(), dateTime)));
	}

	/**
	 * Moves clock by adding a duration.
	 *
	 * @param duration Duration to be added to the clock.
	 */
	public static void moveClockBy(
			final Duration duration) {
		DateTimeHelper.setClock(Clock.offset(DateTimeHelper.getClock(), duration));
	}

	/**
	 * Cleans after each test.
	 */
	@BeforeEach
	public void cleanClockBeforeTest() {
		TestHelper.cleanClock();
	}

	/**
	 * Creates a Postgres container.
	 */
	@SuppressWarnings("resource")
	public static GenericContainer<?> createPostgresContainer() {
		return new GenericContainer<>("coldis/infrastructure-transactional-repository:5.0.9")
				.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCpuCount(getCpuQuota())
						.withMemory(getMemoryQuota()).withDiskQuota(TestHelper.DEFAULT_DISK_QUOTA))
				.withExposedPorts(5432)
				.withEnv(Map.of("ENABLE_JSON_CAST", "true", "ENABLE_UNACCENT", "true", "POSTGRES_ADMIN_PASSWORD", "postgres", "POSTGRES_ADMIN_USER", "postgres",
						"REPLICATOR_USER_NAME", "replicator", "REPLICATOR_USER_PASSWORD", "replicator", "POSTGRES_DEFAULT_USER", TestHelper.TEST_USER_NAME,
						"POSTGRES_DEFAULT_PASSWORD", TestHelper.TEST_USER_PASSWORD, "POSTGRES_DEFAULT_DATABASE", TestHelper.TEST_USER_NAME, "MAX_CONNECTIONS",
						"50"))
				.waitingFor(Wait
						.forSuccessfulCommand(
								"PGPASSWORD=\"" + TestHelper.TEST_USER_PASSWORD + "\" psql -c 'SELECT 1;' -U \"" + TestHelper.TEST_USER_NAME + "\"")
						.withStartupTimeout(Duration.ofMinutes(3)))
				.withStartupAttempts(3);
	}

	/**
	 * Creates an Artemis container.
	 */
	@SuppressWarnings("resource")
	public static GenericContainer<?> createArtemisContainer() {
		return new GenericContainer<>("coldis/infrastructure-messaging-service:2.27")
				.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCpuCount(TestHelper.DEFAULT_CPU_QUOTA)
						.withMemory(TestHelper.DEFAULT_MEMORY_QUOTA).withDiskQuota(TestHelper.DEFAULT_DISK_QUOTA))
				.withExposedPorts(8161, 61616)
				.withEnv(Map.of("JDK_USE_TUNED_OPTS", "false", "ARTEMIS_USERNAME", TestHelper.TEST_USER_NAME, "ARTEMIS_PASSWORD", TestHelper.TEST_USER_PASSWORD,
						"ARTEMIS_PERF_JOURNAL", "ALWAYS"))
				.waitingFor(Wait.forLogMessage(".*AMQ241004.*", 1).withStartupTimeout(Duration.ofMinutes(3))).withStartupAttempts(3);
	}

	/**
	 * Creates a Redis container.
	 */
	@SuppressWarnings("resource")
	public static GenericContainer<?> createRedisContainer() {
		return new GenericContainer<>("redis:7.4.1-bookworm")
				.withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withCpuCount(TestHelper.DEFAULT_CPU_QUOTA)
						.withMemory(TestHelper.DEFAULT_MEMORY_QUOTA).withDiskQuota(TestHelper.DEFAULT_DISK_QUOTA))
				.withExposedPorts(6379).withCommand("redis-server", "--save", "60", "1", "--loglevel", "warning")
				.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3))).withStartupAttempts(3);
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
