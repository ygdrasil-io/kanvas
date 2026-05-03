package org.skia.core

/**
 * C++ original:
 * ```cpp
 * struct SkQuadCoeff {
 *     SkQuadCoeff() {}
 *
 *     SkQuadCoeff(const skvx::float2& A, const skvx::float2& B, const skvx::float2& C)
 *         : fA(A)
 *         , fB(B)
 *         , fC(C)
 *     {
 *     }
 *
 *     SkQuadCoeff(const SkPoint src[3]) {
 *         fC = from_point(src[0]);
 *         auto P1 = from_point(src[1]);
 *         auto P2 = from_point(src[2]);
 *         fB = times_2(P1 - fC);
 *         fA = P2 - times_2(P1) + fC;
 *     }
 *
 *     skvx::float2 eval(const skvx::float2& tt) const {
 *         return (fA * tt + fB) * tt + fC;
 *     }
 *
 *     skvx::float2 fA;
 *     skvx::float2 fB;
 *     skvx::float2 fC;
 * }
 * ```
 */
public data class SkQuadCoeff public constructor(
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
) {
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 eval(const skvx::float2& tt) const {
   *         return (fA * tt + fB) * tt + fC;
   *     }
   * ```
   */
  public fun eval(tt: Float2): Float2 {
    TODO("Implement eval")
  }
}
