package org.skia.core

import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkConicCoeff {
 *     SkConicCoeff(const SkConic& conic) {
 *         skvx::float2 p0 = from_point(conic.fPts[0]);
 *         skvx::float2 p1 = from_point(conic.fPts[1]);
 *         skvx::float2 p2 = from_point(conic.fPts[2]);
 *         skvx::float2 ww(conic.fW);
 *
 *         auto p1w = p1 * ww;
 *         fNumer.fC = p0;
 *         fNumer.fA = p2 - times_2(p1w) + p0;
 *         fNumer.fB = times_2(p1w - p0);
 *
 *         fDenom.fC = 1;
 *         fDenom.fB = times_2(ww - fDenom.fC);
 *         fDenom.fA = 0 - fDenom.fB;
 *     }
 *
 *     skvx::float2 eval(SkScalar t) const {
 *         skvx::float2 tt(t);
 *         skvx::float2 numer = fNumer.eval(tt);
 *         skvx::float2 denom = fDenom.eval(tt);
 *         return numer / denom;
 *     }
 *
 *     SkQuadCoeff fNumer;
 *     SkQuadCoeff fDenom;
 * }
 * ```
 */
public data class SkConicCoeff public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkQuadCoeff fNumer
   * ```
   */
  public var fNumer: SkQuadCoeff,
  /**
   * C++ original:
   * ```cpp
   * SkQuadCoeff fDenom
   * ```
   */
  public var fDenom: SkQuadCoeff,
) {
  /**
   * C++ original:
   * ```cpp
   * skvx::float2 eval(SkScalar t) const {
   *         skvx::float2 tt(t);
   *         skvx::float2 numer = fNumer.eval(tt);
   *         skvx::float2 denom = fDenom.eval(tt);
   *         return numer / denom;
   *     }
   * ```
   */
  public fun eval(t: SkScalar): Float2 {
    TODO("Implement eval")
  }
}
