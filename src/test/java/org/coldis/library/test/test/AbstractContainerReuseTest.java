package org.coldis.library.test.test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import org.coldis.library.test.StartTestWithContainerExtension;
import org.coldis.library.test.TestHelper;
import org.coldis.library.test.TestWithContainer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.GenericContainer;

/**
 * Abstract base test for container reuse verification.
 */
@TestWithContainer(parallel = true)
@ExtendWith(StartTestWithContainerExtension.class)
public abstract class AbstractContainerReuseTest {

	public static GenericContainer<?> POSTGRES_CONTAINER = TestHelper.POSTGRES_CONTAINER;

	public static GenericContainer<?> REDIS_CONTAINER = TestHelper.REDIS_CONTAINER;

	@Test
	public void testPostgresIsRunning() throws Exception {
		Assertions.assertTrue(POSTGRES_CONTAINER.isRunning());
		try (Connection connection = DriverManager.getConnection(
				"jdbc:postgresql://" + System.getProperty("POSTGRES_CONTAINER_IP") + ":5432/" + TestHelper.TEST_USER_NAME,
				TestHelper.TEST_USER_NAME, TestHelper.TEST_USER_PASSWORD);
				Statement statement = connection.createStatement()) {
			final ResultSet result = statement.executeQuery("SELECT 1;");
			result.next();
			Assertions.assertEquals(1, result.getInt(1));
		}
	}

	@Test
	public void testRedisIsRunning() {
		Assertions.assertTrue(REDIS_CONTAINER.isRunning());
	}

}
