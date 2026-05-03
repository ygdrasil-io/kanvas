package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class CrossingAccumulator {
 * public:
 *     void recordCrossing(const Segment& s0, const Segment& s1) {
 *         // Endpoints with no possible interior overlap.
 *         if (s0.upper() == s1.lower() || s0.lower() == s1.upper()) {
 *             return;
 *         }
 *
 *         // Segments don't overlap if they are not collinear.
 *         if ((s0.upper() == s1.upper() || s0.lower() == s1.lower()) && compare_slopes(s0, s1) != 0) {
 *             return;
 *         }
 *
 *         fCrossings.emplace_back(s0, s1);
 *     }
 *
 *     std::vector<Crossing> finishAndReleaseCrossings() {
 *         return std::move(fCrossings);
 *     }
 *
 * private:
 *     std::vector<Crossing> fCrossings;
 * }
 * ```
 */
public data class CrossingAccumulator public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<Crossing> fCrossings
   * ```
   */
  private var fCrossings: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void recordCrossing(const Segment& s0, const Segment& s1) {
   *         // Endpoints with no possible interior overlap.
   *         if (s0.upper() == s1.lower() || s0.lower() == s1.upper()) {
   *             return;
   *         }
   *
   *         // Segments don't overlap if they are not collinear.
   *         if ((s0.upper() == s1.upper() || s0.lower() == s1.lower()) && compare_slopes(s0, s1) != 0) {
   *             return;
   *         }
   *
   *         fCrossings.emplace_back(s0, s1);
   *     }
   * ```
   */
  public fun recordCrossing(s0: Segment, s1: Segment) {
    TODO("Implement recordCrossing")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<Crossing> finishAndReleaseCrossings() {
   *         return std::move(fCrossings);
   *     }
   * ```
   */
  public fun finishAndReleaseCrossings(): Int {
    TODO("Implement finishAndReleaseCrossings")
  }
}
