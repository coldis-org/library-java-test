package org.coldis.library.test.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

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
	 * Tests that a table kept locked (by a session idle in a transaction, which
	 * cancelling backends does not release) fails the truncation, naming the
	 * table, instead of leaving its rows to the next test.
	 */
	@Test
	public void testTruncateTablesFailsWhenATableStaysLocked() throws Exception {
		final String tableName = "truncate_test_locked";
		this.jdbcTemplate.update("CREATE TABLE IF NOT EXISTS " + tableName + " (id BIGINT)");
		this.jdbcTemplate.update("INSERT INTO " + tableName + " (id) VALUES (1)");
		try (Connection lockHolder = this.jdbcTemplate.getDataSource().getConnection()) {
			lockHolder.setAutoCommit(false);
			try (Statement statement = lockHolder.createStatement()) {
				statement.execute("LOCK TABLE " + tableName + " IN ACCESS SHARE MODE");
			}
			try {
				final IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class, () -> this.truncateTables(tableName));
				Assertions.assertTrue(failure.getMessage().contains(tableName));
			}
			finally {
				lockHolder.rollback();
			}
		}
		Assertions.assertEquals(1L, this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + tableName, Long.class));
		this.truncateTables(tableName);
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
