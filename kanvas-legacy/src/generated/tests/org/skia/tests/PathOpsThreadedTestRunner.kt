package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PathOpsThreadedTestRunner {
 * public:
 *     explicit PathOpsThreadedTestRunner(skiatest::Reporter* reporter) : fReporter(reporter) {}
 *
 *     ~PathOpsThreadedTestRunner();
 *
 *     void render();
 *
 * public:
 *     SkTDArray<PathOpsThreadedRunnable*> fRunnables;
 *     skiatest::Reporter* fReporter;
 * }
 * ```
 */
public data class PathOpsThreadedTestRunner public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkTDArray<PathOpsThreadedRunnable*> fRunnables
   * ```
   */
  public var fRunnables: Int,
  /**
   * C++ original:
   * ```cpp
   * skiatest::Reporter* fReporter
   * ```
   */
  public var fReporter: Reporter?,
) {
  /**
   * C++ original:
   * ```cpp
   * void PathOpsThreadedTestRunner::render() {
   *     SkTaskGroup().batch(fRunnables.size(), [&](int i) {
   *         (*fRunnables[i])();
   *     });
   * }
   * ```
   */
  public fun render() {
    TODO("Implement render")
  }
}
