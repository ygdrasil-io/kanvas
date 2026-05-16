package org.skia.tests

import kotlin.Double
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class Timer {
 * public:
 *     /** Starts the timer. */
 *     Timer();
 *
 *     /** Nanoseconds since creation. */
 *     double elapsedNs() const;
 *
 *     /** Milliseconds since creation. */
 *     double elapsedMs() const;
 *
 *     /** Milliseconds since creation as an integer.
 *         Behavior is undefined for durations longer than TimeUtils::MSecMax.
 *     */
 *     TimeUtils::MSec elapsedMsInt() const;
 * private:
 *     double fStartNanos;
 * }
 * ```
 */
public data class Timer public constructor(
  /**
   * C++ original:
   * ```cpp
   * double fStartNanos
   * ```
   */
  private var fStartNanos: Double,
) {
  /**
   * C++ original:
   * ```cpp
   * double skiatest::Timer::elapsedNs() const {
   *     return SkTime::GetNSecs() - fStartNanos;
   * }
   * ```
   */
  public fun elapsedNs(): Double {
    TODO("Implement elapsedNs")
  }

  /**
   * C++ original:
   * ```cpp
   * double skiatest::Timer::elapsedMs() const { return this->elapsedNs() * 1e-6; }
   * ```
   */
  public fun elapsedMs(): Double {
    TODO("Implement elapsedMs")
  }

  /**
   * C++ original:
   * ```cpp
   * TimeUtils::MSec skiatest::Timer::elapsedMsInt() const {
   *     const double elapsedMs = this->elapsedMs();
   *     SkASSERT(TimeUtils::MSecMax >= elapsedMs);
   *     return static_cast<TimeUtils::MSec>(elapsedMs);
   * }
   * ```
   */
  public fun elapsedMsInt(): Int {
    TODO("Implement elapsedMsInt")
  }
}
