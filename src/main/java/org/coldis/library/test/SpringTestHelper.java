package org.coldis.library.test;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.Set;

import javax.sql.DataSource;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Spring test helper.
 */
public class SpringTestHelper extends TestHelper {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(SpringTestHelper.class);

	/** Random. */
	protected static final Random RANDOM = new SecureRandom();

	/** Attempts to truncate each table: the first one plus two retries. */
	private static final int TRUNCATE_ATTEMPTS = 3;

	/**
	 * PostgreSQL states of the lock failures a truncation retries: lock not
	 * available (lock timeout), query cancelled and deadlock detected.
	 */
	private static final Set<String> TRUNCATE_RETRY_SQL_STATES = Set.of("55P03", "57014", "40P01");

	/** PostgreSQL state of a table that does not exist (nothing to truncate). */
	private static final String UNDEFINED_TABLE_SQL_STATE = "42P01";

	/** JDBC template. */
	@Autowired(required = false)
	protected JdbcTemplate jdbcTemplate;

	/** Cache manager. */
	@Autowired(required = false)
	protected CacheManager cacheManager;

	/** Artemis server locator. */
	@Autowired(required = false)
	protected ServerLocator serverLocator;

	/**
	 * Changes the sequence to random.
	 *
	 * @param sequenceName Name of the database sequence.
	 */
	public void changeSequenceToRandom(
			final String sequenceName) {
		final String query = "ALTER SEQUENCE " + sequenceName + " RESTART WITH " + SpringTestHelper.RANDOM.nextInt(Integer.MAX_VALUE);
		this.jdbcTemplate.execute(query);
	}

	/**
	 * Cancels running queries on all other PostgreSQL backends on the current
	 * database.
	 */
	public void cancelOtherPostgresBackends() {
		this.jdbcTemplate.execute(
				"SELECT pg_cancel_backend(pid) FROM pg_stat_activity"
						+ " WHERE pid <> pg_backend_pid() AND datname = current_database()"
						+ " AND state <> 'idle'");
	}

	/**
	 * Truncates one table. A lock failure (lock timeout, cancelled statement or
	 * deadlock) is retried after cancelling the other PostgreSQL backends again (an
	 * async writer of the previous test may have started in between); a table that
	 * does not exist has nothing to truncate. Fails on any other error, or when a
	 * lock failure persists after {@link #TRUNCATE_ATTEMPTS} attempts: continuing
	 * would start the next test on the previous test's rows, and it would fail
	 * somewhere unrelated.
	 *
	 * @param  dataSource            Data source.
	 * @param  table                 Name of the table to truncate.
	 * @throws IllegalStateException If the table could not be truncated.
	 */
	private void truncateTable(
			final DataSource dataSource,
			final String table) {
		SQLException lastFailure = null;
		boolean isTruncatedOrAbsent = false;
		boolean isRetryable = true;
		for (int attempt = 1; !isTruncatedOrAbsent && isRetryable && (attempt <= SpringTestHelper.TRUNCATE_ATTEMPTS); attempt++) {
			if (attempt > 1) {
				this.cancelOtherPostgresBackends();
			}
			try (final Connection connection = dataSource.getConnection()) {
				connection.setAutoCommit(false);
				try (final Statement statement = connection.createStatement()) {
					statement.execute("SET LOCAL lock_timeout = '500ms'");
					statement.execute("TRUNCATE TABLE " + table + " CASCADE");
				}
				connection.commit();
				isTruncatedOrAbsent = true;
			}
			catch (final SQLException exception) {
				// The state can be null (a pool timeout, for example): not retried.
				final String sqlState = exception.getSQLState();
				SpringTestHelper.LOGGER.debug("Truncate attempt {} failed for '{}' ({}): {}", attempt, table, sqlState, exception.getMessage());
				lastFailure = exception;
				isTruncatedOrAbsent = SpringTestHelper.UNDEFINED_TABLE_SQL_STATE.equals(sqlState);
				isRetryable = (sqlState != null) && SpringTestHelper.TRUNCATE_RETRY_SQL_STATES.contains(sqlState);
			}
		}
		if (!isTruncatedOrAbsent) {
			throw new IllegalStateException("Table '" + table + "' could not be truncated, so the test would start on the previous test's rows.",
					lastFailure);
		}
	}

	/**
	 * Truncates the given tables, each with retries and a lock timeout. Cancels
	 * other PostgreSQL backends before truncating to avoid lock contention.
	 *
	 * @param  tableNames            Names of the tables to truncate.
	 * @throws IllegalStateException If a table could not be truncated.
	 */
	public void truncateTables(
			final String... tableNames) {
		this.cancelOtherPostgresBackends();
		final DataSource dataSource = this.jdbcTemplate.getDataSource();
		for (final String table : tableNames) {
			this.truncateTable(dataSource, table);
		}
	}

	/**
	 * Purges all messages from all Artemis queues.
	 */
	public void purgeAllArtemisQueues() {
		if (this.serverLocator == null) {
			return;
		}
		try (ClientSessionFactory factory = this.serverLocator.createSessionFactory();
				ClientSession session = factory.createSession(TestHelper.TEST_USER_NAME, TestHelper.TEST_USER_PASSWORD, false, true, true, false, 1)) {
			session.start();
			try (ClientRequestor requestor = new ClientRequestor(session, "activemq.management")) {
				final ClientMessage listMessage = session.createMessage(false);
				ManagementHelper.putOperationInvocation(listMessage, "broker", "getQueueNames");
				final Object[] queues = (Object[]) ManagementHelper.getResult(requestor.request(listMessage));
				for (final Object queue : queues) {
					final ClientMessage purgeMessage = session.createMessage(false);
					ManagementHelper.putOperationInvocation(purgeMessage, "queue." + queue, "removeAllMessages");
					requestor.request(purgeMessage);
				}
			}
		}
		catch (final Exception exception) {
			SpringTestHelper.LOGGER.warn("Failed to purge Artemis queues: {}", exception.getMessage());
		}
	}

	/**
	 * Clears all caches managed by the cache manager.
	 */
	public void clearAllCaches() {
		if (this.cacheManager != null) {
			this.cacheManager.getCacheNames().forEach(cacheName -> {
				try {
					this.cacheManager.getCache(cacheName).clear();
				}
				catch (final Exception exception) {
					SpringTestHelper.LOGGER.warn("Failed to clear cache '{}': {}", cacheName, exception.getMessage());
				}
			});
		}
	}

}
