package org.skia.core

import kotlin.Boolean
import kotlin.Double
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class DistanceLessThan {
 * public:
 *     DistanceLessThan(double* distances) : fDistances(distances) { }
 *     double* fDistances;
 *     bool operator()(const int one, const int two) const {
 *         return fDistances[one] < fDistances[two];
 *     }
 * }
 * ```
 */
public data class DistanceLessThan public constructor(
  /**
   * C++ original:
   * ```cpp
   * double* fDistances
   * ```
   */
  public var fDistances: Double?,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator()(const int one, const int two) const {
   *         return fDistances[one] < fDistances[two];
   *     }
   * ```
   */
  public operator fun invoke(one: Int, two: Int): Boolean {
    TODO("Implement invoke")
  }
}
