package org.coldis.library.test.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.coldis.library.test.SpringTestHelper;
import org.coldis.library.test.StartTestWithContainerExtension;
import org.coldis.library.test.StopTestWithContainerExtension;
import org.coldis.library.test.TestHelper;
import org.coldis.library.test.TestWithContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;

/**
 * Test helper test.
 */
@TestWithContainer(parallel = true)
@ExtendWith(value = { StartTestWithContainerExtension.class })
@SpringBootTest(
		classes = SpringTestApplication.class,
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class ContainerTestHelperATest extends SpringTestHelper {
	
	/**
	 * Postgres container.
	 */
	public static GenericContainer<?> POSTGRES_CONTAINER = TestHelper.POSTGRES_CONTAINER;

	/**
	 * Artemis container.
	 */
	public static GenericContainer<?> ARTEMIS_CONTAINER = TestHelper.ARTEMIS_CONTAINER;

	/**
	 * Redis container.
	 */
	public static GenericContainer<?> REDIS_CONTAINER = TestHelper.REDIS_CONTAINER;

	/**
	 * Test Postgres container.
	 */
	@Test
	public void testPostgresContainer() throws Exception {
		try (Connection connection = DriverManager.getConnection(
				"jdbc:postgresql://" + System.getProperty("POSTGRES_CONTAINER_IP") + ":5432/" + TestHelper.TEST_USER_NAME, TestHelper.TEST_USER_NAME,
				TestHelper.TEST_USER_PASSWORD)) {
			try (Statement statement = connection.createStatement()) {
				Class.forName("org.postgresql.Driver");
				final String sql = "SELECT 10;";
				final ResultSet queryResult = statement.executeQuery(sql);
				queryResult.next();
				Assertions.assertEquals(10, queryResult.getInt(1));
				Assertions.assertNotNull(System.getProperty("POSTGRES_CONTAINER_IP"));
				Assertions.assertNotNull(System.getProperty("POSTGRES_CONTAINER_5432"));
				System.out.println(System.getProperty("POSTGRES_CONTAINER_IP"));
				System.out.println(System.getProperty("POSTGRES_CONTAINER_5432"));
			}
		}
	}

	/** Tests that truncating tables empties them. */
	@Test
	public void testTruncateTablesEmptiesTheTables() {
		final String tableName = "truncate_test_emptied";
		this.jdbcTemplate.update("CREATE TABLE IF NOT EXISTS " + tableName + " (id BIGINT)");
		this.jdbcTemplate.update("INSERT INTO " + tableName + " (id) VALUES (1), (2)");
		this.truncateTables(tableName);
		Assertions.assertEquals(0L, this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class));
	}

	/**
	 * Tests that a lock held by a session idle in a transaction (as an async flow
	 * of the previous test would hold it between statements), which cancelling
	 * backends does not release, fails the first attempt on the lock timeout and
	 * is released by the retry terminating that session, so the table is
	 * truncated. The holder connects outside the pool, so its termination leaves
	 * no broken connection there.
	 */
	@Test
	public void testTruncateTablesTerminatesAnIdleTransactionHoldingALock() throws Exception {
		final String tableName = "truncate_test_idle_holder";
		this.jdbcTemplate.update("CREATE TABLE IF NOT EXISTS " + tableName + " (id BIGINT)");
		this.jdbcTemplate.update("INSERT INTO " + tableName + " (id) VALUES (1)");
		try (Connection lockHolder = DriverManager.getConnection(
				"jdbc:postgresql://" + System.getProperty("POSTGRES_CONTAINER_IP") + ":5432/" + TestHelper.TEST_USER_NAME, TestHelper.TEST_USER_NAME,
				TestHelper.TEST_USER_PASSWORD)) {
			lockHolder.setAutoCommit(false);
			try (Statement statement = lockHolder.createStatement()) {
				statement.execute("LOCK TABLE " + tableName + " IN ACCESS SHARE MODE");
			}
			this.truncateTables(tableName);
			Assertions.assertEquals(0L, this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class));
			// The retry terminated the holder's session.
			Assertions.assertFalse(lockHolder.isValid(2));
		}
	}

	/**
	 * Reproduces a previous test's async flow still running when the next test
	 * truncates: it holds a transaction that has written to the table and, like a
	 * flow calling remote services between statements, is idle inside it most of
	 * the time (cancelling backends only interrupts its short statements, and the
	 * transaction keeps its locks). The truncation's retry terminates it, so the
	 * table is truncated instead of the next test starting on its rows.
	 */
	@Test
	public void testTruncateTablesTerminatesAnAsyncFlowHoldingItsTransaction() throws Exception {
		final String tableName = "truncate_test_async_flow";
		this.jdbcTemplate.update("CREATE TABLE IF NOT EXISTS " + tableName + " (id BIGINT)");
		this.jdbcTemplate.update("INSERT INTO " + tableName + " (id) VALUES (1)");
		final String asyncFlowStep = "SELECT 'truncate-test-async-flow-step'";
		final CompletableFuture<Void> asyncFlow = CompletableFuture.runAsync(() -> {
			try (Connection connection = DriverManager.getConnection(
					"jdbc:postgresql://" + System.getProperty("POSTGRES_CONTAINER_IP") + ":5432/" + TestHelper.TEST_USER_NAME,
					TestHelper.TEST_USER_NAME, TestHelper.TEST_USER_PASSWORD); Statement statement = connection.createStatement()) {
				connection.setAutoCommit(false);
				statement.execute("UPDATE " + tableName + " SET id = id");
				for (int step = 0; step < 40; step++) {
					Thread.sleep(500);
					statement.execute(asyncFlowStep);
				}
				connection.commit();
			}
			catch (final SQLException exception) {
				throw new CompletionException(exception);
			}
			catch (final InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new CompletionException(exception);
			}
		});
		Assertions.assertTrue(TestHelper.waitUntilValid(
				() -> this.jdbcTemplate.queryForObject(
						"SELECT count(*) FROM pg_stat_activity WHERE state = 'idle in transaction' AND query = ?", Long.class, asyncFlowStep),
				idleFlows -> idleFlows > 0, TestHelper.LONG_WAIT, TestHelper.VERY_SHORT_WAIT), "the async flow must be idle in its transaction");
		this.truncateTables(tableName);
		Assertions.assertEquals(0L, this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class));
		// The async flow's session was terminated by the retry (admin shutdown).
		final ExecutionException asyncFlowFailure = Assertions.assertThrows(ExecutionException.class, asyncFlow::get);
		Assertions.assertEquals("57P01", ((SQLException) asyncFlowFailure.getCause()).getSQLState());
	}

	/**
	 * Tests that a pooled connection whose backend the truncation's retry
	 * terminated is evicted from the pool: the table is truncated, and the pool
	 * keeps handing out working connections afterwards.
	 */
	@Test
	public void testTruncateTablesEvictsTheTerminatedPooledConnection() throws Exception {
		final String tableName = "truncate_test_pooled_holder";
		this.jdbcTemplate.update("CREATE TABLE IF NOT EXISTS " + tableName + " (id BIGINT)");
		this.jdbcTemplate.update("INSERT INTO " + tableName + " (id) VALUES (1)");
		final Connection pooledHolder = this.jdbcTemplate.getDataSource().getConnection();
		pooledHolder.setAutoCommit(false);
		try (Statement statement = pooledHolder.createStatement()) {
			statement.execute("LOCK TABLE " + tableName + " IN ACCESS SHARE MODE");
		}
		this.truncateTables(tableName);
		Assertions.assertEquals(0L, this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class));
		Assertions.assertFalse(pooledHolder.isValid(2));
		pooledHolder.close();
		for (int query = 0; query < 20; query++) {
			Assertions.assertEquals(1, this.jdbcTemplate.queryForObject("SELECT 1", Integer.class));
		}
	}

	/**
	 * Tests that an error other than a lock failure fails at once, naming the
	 * table.
	 *
	 * <p>Not tested, on purpose: a lock failure persisting through every attempt.
	 * The retries terminate every other session holding a transaction, and the
	 * test database disables prepared transactions (the only holders a
	 * termination cannot reach), so no such lock can be held here. That path
	 * throws the same exception as this one, after the last attempt.
	 */
	@Test
	public void testTruncateTablesFailsAtOnceOnANonLockError() {
		final String invalidTableName = "truncate test invalid name";
		final IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, () -> this.truncateTables(invalidTableName));
		Assertions.assertTrue(failure.getMessage().contains(invalidTableName));
		Assertions.assertEquals("42601", ((SQLException) failure.getCause()).getSQLState());
	}

	/** Tests that a table that does not exist has nothing to truncate, instead of failing. */
	@Test
	public void testTruncateTablesSkipsAMissingTable() {
		final String tableName = "truncate_test_missing";
		this.jdbcTemplate.update("DROP TABLE IF EXISTS " + tableName);
		Assertions.assertDoesNotThrow(() -> this.truncateTables(tableName));
	}

	/**
	 * Tests that a lock held by a running statement (as an async writer of the
	 * previous test would) is released by cancelling the other backends, so the
	 * table is truncated.
	 */
	@Test
	public void testTruncateTablesCancelsARunningLockHolder() throws Exception {
		final String tableName = "truncate_test_running_holder";
		this.jdbcTemplate.update("CREATE TABLE IF NOT EXISTS " + tableName + " (id BIGINT)");
		this.jdbcTemplate.update("INSERT INTO " + tableName + " (id) VALUES (1)");
		final String holderQuery = "SELECT count(*) FROM " + tableName + ", pg_sleep(30)";
		final CompletableFuture<Void> lockHolder = CompletableFuture.runAsync(() -> {
			try (Connection connection = this.jdbcTemplate.getDataSource().getConnection();
					Statement statement = connection.createStatement()) {
				statement.execute(holderQuery);
			}
			catch (final SQLException exception) {
				throw new CompletionException(exception);
			}
		});
		TestHelper.waitUntilValid(
				() -> this.jdbcTemplate.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND query = ?", Long.class,
						holderQuery),
				runningHolders -> runningHolders > 0, TestHelper.LONG_WAIT, TestHelper.VERY_SHORT_WAIT);
		this.truncateTables(tableName);
		Assertions.assertEquals(0L, this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class));
		final ExecutionException holderFailure = Assertions.assertThrows(ExecutionException.class, lockHolder::get);
		Assertions.assertEquals("57014", ((SQLException) holderFailure.getCause()).getSQLState());
	}

	/** Tests updating the sequence to random. */
	@Test
	public void testChangeSequenceToRandom() {
		final String sequenceName = "test_sequence";
		this.jdbcTemplate.update("CREATE SEQUENCE IF NOT EXISTS " + sequenceName);
		this.changeSequenceToRandom(sequenceName);
		final Long sequenceValue = this.jdbcTemplate.queryForObject("SELECT nextval(?)", Long.class, sequenceName);
		Assertions.assertNotNull(sequenceValue);
		Assertions.assertTrue(sequenceValue > 0);
	}

}
