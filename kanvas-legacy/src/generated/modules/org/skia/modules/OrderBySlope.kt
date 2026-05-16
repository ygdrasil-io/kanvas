package org.skia.modules

import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * struct OrderBySlope {
 *     bool operator()(const Segment& s0, const Segment& s1) const;
 * }
 * ```
 */
public open class OrderBySlope {
  /**
   * C++ original:
   * ```cpp
   * bool OrderBySlope::operator()(const bentleyottmann::Segment& s0,
   *                               const bentleyottmann::Segment& s1) const {
   *     return compare_slopes(s0, s1) < 0;
   * }
   * ```
   */
  public operator fun invoke(s0: Segment, s1: Segment): Boolean {
    TODO("Implement invoke")
  }
}
