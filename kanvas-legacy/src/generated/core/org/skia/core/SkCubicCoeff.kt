package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct SkCubicCoeff {
 *     SkCubicCoeff(const SkPoint src[4]) {
 *         skvx::float2 P0 = from_point(src[0]);
 *         skvx::float2 P1 = from_point(src[1]);
 *         skvx::float2 P2 = from_point(src[2]);
 *         skvx::float2 P3 = from_point(src[3]);
 *         skvx::float2 three(3);
 *         fA = P3 + three * (P1 - P2) - P0;
 *         fB = three * (P2 - times_2(P1) + P0);
 *         fC = three * (P1 - P0);
 *         fD = P0;
 *     }
 *
 *     skvx::float2 eval(const skvx::float2& t) const {
 *         return ((fA * t + fB) * t + fC) * t + fD;
 *     }
 *
 *     skvx::float2 fA;
 *     skvx::float2 fB;
 *     skvx::float2 fC;
 *     skvx::float2 fD;
 * }
 * ```
 */
public data class SkCubicCoeff public constructor(
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 fA
   * ```
   */
  public var fA: Float2,
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 fB
   * ```
   */
  public var fB: Float2,
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 fC
   * ```
   */
  public var fC: Float2,
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 fD
   * ```
   */
  public var fD: Float2,
) {
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 eval(const skvx::float2& t) const {
   *         return ((fA * t + fB) * t + fC) * t + fD;
   *     }
   * ```
   */
  public fun eval(t: Float2): Float2 {
    TODO("Implement eval")
  }
}
