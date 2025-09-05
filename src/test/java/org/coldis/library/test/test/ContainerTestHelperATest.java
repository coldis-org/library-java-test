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
@TestWithContainer(parallel = true, reuse = true)
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
