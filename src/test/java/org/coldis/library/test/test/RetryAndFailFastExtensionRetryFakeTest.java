package org.coldis.library.test.test;

import org.coldis.library.test.TestWithRetryAndFailFast;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test for the retry and fail fast extension.
 */
@TestWithRetryAndFailFast
public class RetryAndFailFastExtensionRetryFakeTest {
  /**
   * Test retry.
   */
  public static Integer TEST1_RETRIES = 0;

  /**
   * Setup.
   */
  @BeforeAll
  public static void setup() {
    RetryAndFailFastExtensionRetryFakeTest.TEST1_RETRIES = 0;
  }

  /**
   * Test retry.
   */
  @Test
  public void failTwice() {
    RetryAndFailFastExtensionRetryFakeTest.TEST1_RETRIES++;
    if (RetryAndFailFastExtensionRetryFakeTest.TEST1_RETRIES < 3) {
      throw new RuntimeException("Fake test failure, retrying...");
    }
  }
}
