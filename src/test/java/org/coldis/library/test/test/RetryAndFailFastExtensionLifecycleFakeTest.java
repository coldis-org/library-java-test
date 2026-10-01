package org.coldis.library.test.test;

import java.util.ArrayList;
import java.util.List;

import org.coldis.library.test.TestWithRetryAndFailFast;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Retried test whose @BeforeEach saves and changes shared state that its @AfterEach restores: the
 * state must be back to its original value once the retried test is done, and each attempt must run
 * @BeforeEach, the test and @AfterEach, in that order.
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
   * Lifecycle calls, in order.
   */
  public static final List<String> CALLS = new ArrayList<>();

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
    RetryAndFailFastExtensionLifecycleFakeTest.CALLS.clear();
  }

  /**
   * Saves and changes the shared state.
   */
  @BeforeEach
  public void changeState() {
    RetryAndFailFastExtensionLifecycleFakeTest.CALLS.add("before");
    this.original = RetryAndFailFastExtensionLifecycleFakeTest.STATE;
    RetryAndFailFastExtensionLifecycleFakeTest.STATE = "changed-by-test";
  }

  /**
   * Restores the shared state.
   */
  @AfterEach
  public void restoreState() {
    RetryAndFailFastExtensionLifecycleFakeTest.CALLS.add("after");
    RetryAndFailFastExtensionLifecycleFakeTest.STATE = this.original;
  }

  /**
   * Fails on the first run only.
   */
  @Test
  public void failOnce() {
    RetryAndFailFastExtensionLifecycleFakeTest.RUNS++;
    RetryAndFailFastExtensionLifecycleFakeTest.CALLS.add("test");
    if (RetryAndFailFastExtensionLifecycleFakeTest.RUNS < 2) {
      throw new RuntimeException("Fake test failure, retrying...");
    }
  }

}
