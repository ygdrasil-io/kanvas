package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Upper {
 *     Segment s;
 *
 *     // Arbitrary comparison for queue uniqueness.
 *     friend bool operator< (const Upper& u0, const Upper& u1) {
 *         return std::tie(u0.s.p0, u0.s.p1) < std::tie(u1.s.p0, u1.s.p1);
 *     }
 * }
 * ```
 */
public data class Upper public constructor(
  /**
   * C++ original:
   * ```cpp
   * Segment s
   * ```
   */
  public var s: Int,
)
