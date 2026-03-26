package org.coldis.library.test;

import java.security.SecureRandom;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

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

	/**
	 * Changes the sequence to random.
	 */
	public void changeSequenceToRandom(
			final String sequenceName) {
		final String query = "ALTER SEQUENCE " + sequenceName + " RESTART WITH " + SpringTestHelper.RANDOM.nextInt(Integer.MAX_VALUE);
		this.jdbcTemplate.execute(query);
	}

}
