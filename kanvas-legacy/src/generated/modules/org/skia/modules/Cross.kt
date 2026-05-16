package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Cross {
 *     Segment s0;
 *     Segment s1;
 *
 *     // Arbitrary comparison for queue uniqueness.
 *     friend bool operator< (const Cross& c0, const Cross& c1) {
 *         return std::tie(c0.s0.p0, c0.s0.p1, c0.s1.p0, c0.s1.p1) <
 *                     std::tie(c1.s0.p0, c1.s0.p1, c1.s1.p0, c1.s1.p1);
 *     }
 * }
 * ```
 */
public data class Cross public constructor(
  /**
   * C++ original:
   * ```cpp
   * Segment s0
   * ```
   */
  public var s0: Int,
  /**
   * C++ original:
   * ```cpp
   * Segment s1
   * ```
   */
  public var s1: Int,
)
