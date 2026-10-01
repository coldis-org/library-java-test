package org.coldis.library.test.test;

import org.coldis.library.test.TestWithRetryAndFailFast;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Retried test whose @BeforeEach saves and changes shared state that its @AfterEach restores: the
 * state must be back to its original value once the retried test is done, and each @BeforeEach must
 * be matched by exactly one @AfterEach.
 */
@TestWithRetryAndFailFast
public class RetryAndFailFastExtensionLifecycleFakeTest {

  /**
   * Original value of the shared state.
   */
  public static final String CONFIGURED = "configured";

  /**
   * Shared state the test changes.
   */
  public static String STATE = RetryAndFailFastExtensionLifecycleFakeTest.CONFIGURED;

  /**
   * Test runs.
   */
  public static Integer RUNS = 0;

  /**
   * @BeforeEach runs.
   */
  public static Integer BEFORE_EACH_RUNS = 0;

  /**
   * @AfterEach runs.
   */
  public static Integer AFTER_EACH_RUNS = 0;

  /**
   * State saved by @BeforeEach.
   */
  private String original;

  /**
   * Setup.
   */
  @BeforeAll
  public static void setup() {
    RetryAndFailFastExtensionLifecycleFakeTest.STATE = RetryAndFailFastExtensionLifecycleFakeTest.CONFIGURED;
    RetryAndFailFastExtensionLifecycleFakeTest.RUNS = 0;
    RetryAndFailFastExtensionLifecycleFakeTest.BEFORE_EACH_RUNS = 0;
    RetryAndFailFastExtensionLifecycleFakeTest.AFTER_EACH_RUNS = 0;
  }

  /**
   * Saves and changes the shared state.
   */
  @BeforeEach
  public void changeState() {
    RetryAndFailFastExtensionLifecycleFakeTest.BEFORE_EACH_RUNS++;
    this.original = RetryAndFailFastExtensionLifecycleFakeTest.STATE;
    RetryAndFailFastExtensionLifecycleFakeTest.STATE = "changed-by-test";
  }

  /**
   * Restores the shared state.
   */
  @AfterEach
  public void restoreState() {
    RetryAndFailFastExtensionLifecycleFakeTest.AFTER_EACH_RUNS++;
    RetryAndFailFastExtensionLifecycleFakeTest.STATE = this.original;
  }

  /**
   * Fails on the first run only.
   */
  @Test
  public void failOnce() {
    RetryAndFailFastExtensionLifecycleFakeTest.RUNS++;
    if (RetryAndFailFastExtensionLifecycleFakeTest.RUNS < 2) {
      throw new RuntimeException("Fake test failure, retrying...");
    }
  }

}
