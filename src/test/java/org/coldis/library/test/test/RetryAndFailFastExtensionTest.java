package org.coldis.library.test.test;

import org.coldis.library.test.TestWithRetryAndFailFast;
import org.coldis.library.test.retry.RetryExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/**
 * Test for the retry and fail fast extension.
 */
@TestWithRetryAndFailFast
public class RetryAndFailFastExtensionTest {
  /**
   * Test retry.
   */
  @Test
  public void testRetry() {

    // Executes a fake test.
    final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
      .selectors(DiscoverySelectors.selectClass(RetryAndFailFastExtensionRetryFakeTest.class))
      .configurationParameter("junit.jupiter.conditions.deactivate", "org.junit.jupiter.api.condition.DisabledCondition")
      .build();
    final Launcher launcher = LauncherFactory.create();
    final SummaryGeneratingListener listener = new SummaryGeneratingListener();
    launcher.registerTestExecutionListeners(listener);
    final Long testStartTime = System.currentTimeMillis();
    launcher.execute(request);
    final Long testFinishTime = System.currentTimeMillis();

    // Validates the test execution summary.
    Assertions.assertEquals(3, RetryAndFailFastExtensionRetryFakeTest.TEST1_RETRIES, "The test should have been tried 3 times.");
    Assertions.assertEquals(1, listener.getSummary().getTestsFoundCount());
    Assertions.assertEquals(1, listener.getSummary().getTestsSucceededCount());
    Assertions.assertEquals(0, listener.getSummary().getTotalFailureCount());
    Assertions.assertTrue((testFinishTime - testStartTime) > (RetryExtension.FIXED_DELAY_BEFORE_NEXT_ATTEMPT * 3L));
  }
}
