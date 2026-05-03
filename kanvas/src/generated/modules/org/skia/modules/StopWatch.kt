package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class StopWatch {
 * public:
 *     void start() {
 *         fStart = std::chrono::high_resolution_clock::now();
 *     }
 *
 *     void stop() {
 *         std::chrono::high_resolution_clock::time_point stop =
 *                 std::chrono::high_resolution_clock::now();
 *
 *         fAccumulatedTime += std::chrono::duration_cast<std::chrono::microseconds>(stop - fStart);
 *         fCount += 1;
 *     }
 *
 *     void print() {
 *         int64_t average = fAccumulatedTime.count() / fCount;
 *         SkDebugf("average time: %" PRId64 " µs\n", average);
 *     }
 *
 * private:
 *     int fCount = 0;
 *     std::chrono::high_resolution_clock::time_point fStart;
 *     std::chrono::microseconds fAccumulatedTime = std::chrono::microseconds::zero();
 * }
 * ```
 */
public data class StopWatch public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fCount = 0
   * ```
   */
  private var fCount: Int,
  /**
   * C++ original:
   * ```cpp
   * std::chrono::high_resolution_clock::time_point fStart
   * ```
   */
  private var fStart: Int,
  /**
   * C++ original:
   * ```cpp
   * std::chrono::microseconds fAccumulatedTime
   * ```
   */
  private var fAccumulatedTime: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void start() {
   *         fStart = std::chrono::high_resolution_clock::now();
   *     }
   * ```
   */
  public fun start() {
    TODO("Implement start")
  }

  /**
   * C++ original:
   * ```cpp
   * void stop() {
   *         std::chrono::high_resolution_clock::time_point stop =
   *                 std::chrono::high_resolution_clock::now();
   *
   *         fAccumulatedTime += std::chrono::duration_cast<std::chrono::microseconds>(stop - fStart);
   *         fCount += 1;
   *     }
   * ```
   */
  public fun stop() {
    TODO("Implement stop")
  }

  /**
   * C++ original:
   * ```cpp
   * void print() {
   *         int64_t average = fAccumulatedTime.count() / fCount;
   *         SkDebugf("average time: %" PRId64 " µs\n", average);
   *     }
   * ```
   */
  public fun print() {
    TODO("Implement print")
  }
}
