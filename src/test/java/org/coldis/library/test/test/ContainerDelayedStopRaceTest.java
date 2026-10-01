package org.coldis.library.test.test;

import java.util.concurrent.atomic.AtomicInteger;

import org.coldis.library.test.TestHelper;
import org.coldis.library.test.TestWithContainerExtensionHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * Verifies that a delayed stop scheduled before a later release does not stop
 * the container right after that release. No Docker needed: the container only
 * counts {@code stop()} calls. The first timer gets a short delay and the later
 * release a long one, so each step has a wide margin and the later release
 * always happens well before the first timer wakes.
 */
public class ContainerDelayedStopRaceTest {

	/** Delay of the first (stale) timer, in seconds. */
	private static final long STALE_STOP_DELAY = 1;

	/** Delay of the latest release's timer, in seconds. */
	private static final long LATEST_STOP_DELAY = 3;

	/** Container that is never started and only counts stops. */
	private static class StopCountingContainer extends GenericContainer<StopCountingContainer> {

		/** Stop calls. */
		private final AtomicInteger stops = new AtomicInteger();

		/** Constructor. */
		StopCountingContainer() {
			super("postgres:16");
		}

		@Override
		public void stop() {
			this.stops.incrementAndGet();
		}

	}

	/**
	 * Asserts the container survives the stale timer (~1s) and is stopped once
	 * by the latest release's timer (~3.3s).
	 *
	 * @param  container The container.
	 * @param  startTime When the stale timer was scheduled.
	 * @throws Exception If the test fails.
	 */
	private static void assertOnlyLatestTimerStops(
			final StopCountingContainer container,
			final long startTime) throws Exception {
		// Stale timer has woken (~1.0s); the container must still be up.
		Thread.sleep(Math.max(0, 1500 - (System.currentTimeMillis() - startTime)));
		Assertions.assertEquals(0, container.stops.get(), "Stopped by the timer scheduled before the latest release");

		// Latest timer wakes (~3.3s); now it stops, once.
		Assertions.assertTrue(TestHelper.waitUntilValid(container.stops::get, stops -> stops == 1, 10_000, 100));
	}

	/**
	 * Class A releases (stale timer), class B acquires and releases before the
	 * stale timer wakes (latest timer).
	 *
	 * @throws Exception If the test fails.
	 */
	@Test
	public void testStaleTimerDoesNotStopContainerRightAfterLaterRelease() throws Exception {
		final String containerKey = "DELAYED_STOP_RACE_CONTAINER";
		final StopCountingContainer container = new StopCountingContainer();

		// Class A.
		final long startTime = System.currentTimeMillis();
		TestWithContainerExtensionHelper.acquireContainer(containerKey);
		TestWithContainerExtensionHelper.releaseContainer(containerKey);
		TestWithContainerExtensionHelper.scheduleDelayedStop(containerKey, container, ContainerDelayedStopRaceTest.STALE_STOP_DELAY);

		// Class B, releasing ~0.6s before the stale timer wakes.
		Thread.sleep(200);
		TestWithContainerExtensionHelper.acquireContainer(containerKey);
		Thread.sleep(200);
		TestWithContainerExtensionHelper.releaseContainer(containerKey);
		TestWithContainerExtensionHelper.scheduleDelayedStop(containerKey, container, ContainerDelayedStopRaceTest.LATEST_STOP_DELAY);

		ContainerDelayedStopRaceTest.assertOnlyLatestTimerStops(container, startTime);
	}

	/**
	 * Classes A and B hold the container in parallel. A releases (stale timer),
	 * then B releases before the stale timer wakes (latest timer), with no
	 * acquire in between.
	 *
	 * @throws Exception If the test fails.
	 */
	@Test
	public void testStaleTimerDoesNotStopContainerRightAfterParallelRelease() throws Exception {
		final String containerKey = "DELAYED_STOP_PARALLEL_RACE_CONTAINER";
		final StopCountingContainer container = new StopCountingContainer();

		// Classes A and B acquire in parallel.
		TestWithContainerExtensionHelper.acquireContainer(containerKey);
		TestWithContainerExtensionHelper.acquireContainer(containerKey);

		// Class A releases.
		final long startTime = System.currentTimeMillis();
		TestWithContainerExtensionHelper.releaseContainer(containerKey);
		TestWithContainerExtensionHelper.scheduleDelayedStop(containerKey, container, ContainerDelayedStopRaceTest.STALE_STOP_DELAY);

		// Class B releases ~0.7s before the stale timer wakes.
		Thread.sleep(300);
		TestWithContainerExtensionHelper.releaseContainer(containerKey);
		TestWithContainerExtensionHelper.scheduleDelayedStop(containerKey, container, ContainerDelayedStopRaceTest.LATEST_STOP_DELAY);

		ContainerDelayedStopRaceTest.assertOnlyLatestTimerStops(container, startTime);
	}

}
