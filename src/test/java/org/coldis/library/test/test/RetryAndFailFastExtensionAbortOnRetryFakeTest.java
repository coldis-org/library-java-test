package org.coldis.library.test.test;

import org.coldis.library.test.TestWithRetryAndFailFast;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test that fails, then aborts on the retry, for the retry and fail fast extension: the abort must not end the
 * retries and hide the failure.
 */
@TestWithRetryAndFailFast
public class RetryAndFailFastExtensionAbortOnRetryFakeTest {

  /**
   * Test runs.
   */
  public static Integer RUNS = 0;

  /**
   * Setup.
   */
  @BeforeAll
  public static void setup() {
    RetryAndFailFastExtensionAbortOnRetryFakeTest.RUNS = 0;
  }

  /**
   * Fails on the first run, aborts on the second and passes on the third.
   */
  @Test
  public void failThenAbortThenPass() {
    RetryAndFailFastExtensionAbortOnRetryFakeTest.RUNS++;
    if (RetryAndFailFastExtensionAbortOnRetryFakeTest.RUNS == 1) {
      throw new RuntimeException("Fake test failure, retrying...");
    }
    if (RetryAndFailFastExtensionAbortOnRetryFakeTest.RUNS == 2) {
      Assumptions.abort("Fake abort on retry, must not end the retries");
    }
  }

}
