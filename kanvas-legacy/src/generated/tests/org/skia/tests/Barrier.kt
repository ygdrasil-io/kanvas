package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class Barrier {
 * public:
 *     Barrier(int threadCount) : fThreadCount(threadCount) { }
 *     void waitForAll() {
 *         fThreadCount -= 1;
 *         while (fThreadCount > 0) { }
 *     }
 *
 * private:
 *     std::atomic<int> fThreadCount;
 * }
 * ```
 */
public data class Barrier public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::atomic<int> fThreadCount
   * ```
   */
  private var fThreadCount: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void waitForAll() {
   *         fThreadCount -= 1;
   *         while (fThreadCount > 0) { }
   *     }
   * ```
   */
  public fun waitForAll() {
    TODO("Implement waitForAll")
  }
}
