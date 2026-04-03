package org.coldis.library.test;

import java.security.SecureRandom;
import java.sql.Connection;
import java.util.Random;

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
	 * Truncates the given tables with per-table retries and lock timeout. Cancels
	 * other PostgreSQL backends before truncating to avoid lock contention.
	 *
	 * @param tableNames Names of the tables to truncate.
	 */
	public void truncateTables(
			final String... tableNames) {
		this.cancelOtherPostgresBackends();
		final javax.sql.DataSource dataSource = this.jdbcTemplate.getDataSource();
		for (final String table : tableNames) {
			for (int attempt = 0; attempt < 3; attempt++) {
				try (final Connection connection = dataSource.getConnection()) {
					connection.setAutoCommit(false);
					connection.createStatement().execute("SET LOCAL lock_timeout = '500ms'");
					connection.createStatement().execute("TRUNCATE TABLE " + table + " CASCADE");
					connection.commit();
					break;
				}
				catch (final Exception exception) {
					SpringTestHelper.LOGGER.debug("Truncate attempt {} failed for '{}': {}", attempt + 1, table, exception.getMessage());
				}
			}
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
