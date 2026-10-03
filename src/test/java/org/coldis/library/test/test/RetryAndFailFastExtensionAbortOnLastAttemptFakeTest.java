package org.coldis.library.test.test;

import org.coldis.library.test.retry.RetryExtension;
import org.coldis.library.test.retry.TestWithRetry;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test that fails on every attempt but the last, which aborts, for the retry and fail fast extension: the abort
 * must not turn the failure into a skip. Disabled so the build does not run (and fail on) it directly; only
 * {@link RetryAndFailFastExtensionTest} runs it. Retry only, without fail fast: its failure must not set the
 * fail-fast flag and skip the rest of the run.
 */
@Disabled("Ends failed by design; run by RetryAndFailFastExtensionTest")
@TestWithRetry
public class RetryAndFailFastExtensionAbortOnLastAttemptFakeTest {

  /**
   * Test runs.
   */
  public static Integer RUNS = 0;

  /**
   * Setup.
   */
  @BeforeAll
  public static void setup() {
    RetryAndFailFastExtensionAbortOnLastAttemptFakeTest.RUNS = 0;
  }

  /**
   * Fails on every run but the last, which aborts.
   */
  @Test
  public void failThenAbortOnLastAttempt() {
    RetryAndFailFastExtensionAbortOnLastAttemptFakeTest.RUNS++;
    if (RetryAndFailFastExtensionAbortOnLastAttemptFakeTest.RUNS < RetryExtension.getMaxAttempts()) {
      throw new RuntimeException("Fake test failure, retrying...");
    }
    Assumptions.abort("Fake abort on the last attempt, must not hide the failure");
  }

}
