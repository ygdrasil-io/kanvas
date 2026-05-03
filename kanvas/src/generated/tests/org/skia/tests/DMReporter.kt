package org.skia.tests

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct DMReporter : public skiatest::Reporter {
 *     void reportFailed(const skiatest::Failure& failure) override {
 *         fail(failure.toString());
 *     }
 *     bool allowExtendedTest() const override {
 *         return FLAGS_pathOpsExtended;
 *     }
 *     bool verbose() const override { return FLAGS_veryVerbose; }
 * }
 * ```
 */
public open class DMReporter : Reporter() {
  /**
   * C++ original:
   * ```cpp
   * void reportFailed(const skiatest::Failure& failure) override {
   *         fail(failure.toString());
   *     }
   * ```
   */
  public override fun reportFailed(failure: Failure) {
    TODO("Implement reportFailed")
  }

  /**
   * C++ original:
   * ```cpp
   * bool allowExtendedTest() const override {
   *         return FLAGS_pathOpsExtended;
   *     }
   * ```
   */
  public override fun allowExtendedTest(): Boolean {
    TODO("Implement allowExtendedTest")
  }

  /**
   * C++ original:
   * ```cpp
   * bool verbose() const override { return FLAGS_veryVerbose; }
   * ```
   */
  public override fun verbose(): Boolean {
    TODO("Implement verbose")
  }
}
