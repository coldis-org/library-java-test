package org.coldis.library.test.failfast;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * JUnit 5 extension that enables fail-fast behavior across a test run.
 * <p>
 * When enabled, the first test failure creates a filesystem flag
 * (target/test-failed.lock). Subsequent tests are short-circuited (skipped)
 * based on this flag, preventing running further tests once a failure occurs.
 * </p>
 *
 * <h2>Configuration</h2>
 * Control whether fail-fast is active using the system property:
 * <ul>
 *   <li>project.config.source.test.retry-and-fail-fast.fail-fast-enabled — set to
 *       "true" to activate.</li>
 * </ul>
 *
 * <h2>How to use</h2>
 * Prefer the meta-annotation {@link org.coldis.library.test.failfast.TestWithFailFast}
 * on your test classes:
 * <pre>
 * {@code
 * @TestWithFailFast
 * class MySuite { ... }
 * }
 * </pre>
 * Or combine with retry using {@link org.coldis.library.test.TestWithRetryAndFailFast}.
 * You can also register directly with {@code @ExtendWith(FailFastExtension.class)}.
 *
 * <h2>Notes</h2>
 * - The lock file persists under target/ for the life of the JVM execution; a fresh
 *   build/test run will start clean. If needed, you can delete the file manually.
 */
public class FailFastExtension implements BeforeEachCallback, TestWatcher {
  private static final Log LOGGER = LogFactory.getLog(FailFastExtension.class);

  /** Path to the lock file signaling a prior failure. */
  private static final Path FAIL_FAST_FLAG = Paths.get("target/test-failed.lock");

  /**
   * @return whether a failure has been recorded in this run (lock file exists)
   */
  public static boolean hasFailed() {
    return Files.exists(FAIL_FAST_FLAG);
  }

  /**
   * @return whether fail-fast is enabled via system property
   */
  public static boolean isFailFastEnabled() {
    return Objects.equals(System.getProperty("project.config.source.test.retry-and-fail-fast.fail-fast-enabled"), "true");
  }

  /**
   * Skips test execution if the fail-fast flag has been set by a prior failure.
   */
  @Override
  public void beforeEach(final ExtensionContext context) {
    Assumptions.assumeFalse(FailFastExtension.hasFailed(), "A test has already failed, skipping remaining tests.");
  }

  /**
   * Records a failure by creating the lock file when fail-fast is enabled.
   */
  @Override
  public void testFailed(final ExtensionContext context, final Throwable cause) {
    // If the test method failed after all attempts, set the fail fast flag and throw the exception.
    if (FailFastExtension.isFailFastEnabled()) {
      try {
        Files.createFile(FAIL_FAST_FLAG);
      } catch (final FileAlreadyExistsException ignore) {
      } catch (final IOException e) {
        LOGGER.error("Error creating fail fast flag file: " + e.getMessage(), e);
      }
    }
  }
}
