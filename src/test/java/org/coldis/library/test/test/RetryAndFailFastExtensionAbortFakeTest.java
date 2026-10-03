package org.coldis.library.test.test;

import org.coldis.library.test.TestWithRetryAndFailFast;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Aborted test for the retry and fail fast extension: it must run once, not be retried.
 */
@TestWithRetryAndFailFast
public class RetryAndFailFastExtensionAbortFakeTest {

  /**
   * Test runs.
   */
  public static Integer ABORT_RUNS = 0;

  /**
   * Setup.
   */
  @BeforeAll
  public static void setup() {
    RetryAndFailFastExtensionAbortFakeTest.ABORT_RUNS = 0;
  }

  /**
   * Aborts on every run.
   */
  @Test
  public void abortEveryRun() {
    RetryAndFailFastExtensionAbortFakeTest.ABORT_RUNS++;
    Assumptions.abort("Fake abort, must not be retried");
  }

}
