package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.IntArray
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * class SK_API SkCubicMap {
 * public:
 *     SkCubicMap(SkPoint p1, SkPoint p2);
 *
 *     static bool IsLinear(SkPoint p1, SkPoint p2) {
 *         return SkScalarNearlyEqual(p1.fX, p1.fY) && SkScalarNearlyEqual(p2.fX, p2.fY);
 *     }
 *
 *     float computeYFromX(float x) const;
 *
 *     SkPoint computeFromT(float t) const;
 *
 * private:
 *     enum Type {
 *         kLine_Type,     // x == y
 *         kCubeRoot_Type, // At^3 == x
 *         kSolver_Type,   // general monotonic cubic solver
 *     };
 *
 *     SkPoint fCoeff[3];
 *     Type    fType;
 * }
 * ```
 */
public data class SkCubicMap public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fCoeff[3]
   * ```
   */
  private var fCoeff: IntArray,
  /**
   * C++ original:
   * ```cpp
   * Type    fType
   * ```
   */
  private var fType: Type,
) {
  /**
   * C++ original:
   * ```cpp
   * float SkCubicMap::computeYFromX(float x) const {
   *     x = SkTPin(x, 0.0f, 1.0f);
   *
   *     if (nearly_zero(x) || nearly_zero(1 - x)) {
   *         return x;
   *     }
   *     if (fType == kLine_Type) {
   *         return x;
   *     }
   *     float t;
   *     if (fType == kCubeRoot_Type) {
   *         t = std::pow(x / fCoeff[0].fX, 1.0f / 3);
   *     } else {
   *         t = compute_t_from_x(fCoeff[0].fX, fCoeff[1].fX, fCoeff[2].fX, x);
   *     }
   *     float a = fCoeff[0].fY;
   *     float b = fCoeff[1].fY;
   *     float c = fCoeff[2].fY;
   *     float y = ((a * t + b) * t + c) * t;
   *
   *     return y;
   * }
   * ```
   */
  public fun computeYFromX(x: Float): Float {
    TODO("Implement computeYFromX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint SkCubicMap::computeFromT(float t) const {
   *     auto a = skvx::float2::Load(&fCoeff[0]);
   *     auto b = skvx::float2::Load(&fCoeff[1]);
   *     auto c = skvx::float2::Load(&fCoeff[2]);
   *
   *     SkPoint result;
   *     (((a * t + b) * t + c) * t).store(&result);
   *     return result;
   * }
   * ```
   */
  public fun computeFromT(t: Float): Int {
    TODO("Implement computeFromT")
  }

  public enum class Type {
    kLine_Type,
    kCubeRoot_Type,
    kSolver_Type,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool IsLinear(SkPoint p1, SkPoint p2) {
     *         return SkScalarNearlyEqual(p1.fX, p1.fY) && SkScalarNearlyEqual(p2.fX, p2.fY);
     *     }
     * ```
     */
    public fun isLinear(p1: SkPoint, p2: SkPoint): Boolean {
      TODO("Implement isLinear")
    }
  }
}
