package org.skia.core

import kotlin.Boolean
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkHalfPlane {
 *     SkScalar fA, fB, fC;
 *
 *     SkScalar eval(SkScalar x, SkScalar y) const {
 *         return fA * x + fB * y + fC;
 *     }
 *     SkScalar operator()(SkScalar x, SkScalar y) const { return this->eval(x, y); }
 *
 *     bool normalize() {
 *         double a = fA;
 *         double b = fB;
 *         double c = fC;
 *         double dmag = sqrt(a * a + b * b);
 *         // length of initial plane normal is zero
 *         if (dmag == 0) {
 *            fA = fB = 0;
 *            fC = SK_Scalar1;
 *            return true;
 *         }
 *         double dscale = sk_ieee_double_divide(1.0, dmag);
 *         a *= dscale;
 *         b *= dscale;
 *         c *= dscale;
 *         // check if we're not finite, or normal is zero-length
 *         if (!SkIsFinite(a, b, c) ||
 *             (a == 0 && b == 0)) {
 *             fA = fB = 0;
 *             fC = SK_Scalar1;
 *             return false;
 *         }
 *         fA = a;
 *         fB = b;
 *         fC = c;
 *         return true;
 *     }
 *
 *     enum Result {
 *         kAllNegative,
 *         kAllPositive,
 *         kMixed
 *     };
 *     Result test(const SkRect& bounds) const {
 *         // check whether the diagonal aligned with the normal crosses the plane
 *         SkPoint diagMin, diagMax;
 *         if (fA >= 0) {
 *             diagMin.fX = bounds.fLeft;
 *             diagMax.fX = bounds.fRight;
 *         } else {
 *             diagMin.fX = bounds.fRight;
 *             diagMax.fX = bounds.fLeft;
 *         }
 *         if (fB >= 0) {
 *             diagMin.fY = bounds.fTop;
 *             diagMax.fY = bounds.fBottom;
 *         } else {
 *             diagMin.fY = bounds.fBottom;
 *             diagMax.fY = bounds.fTop;
 *         }
 *         SkScalar test = this->eval(diagMin.fX, diagMin.fY);
 *         SkScalar sign = test*this->eval(diagMax.fX, diagMax.fY);
 *         if (sign > 0) {
 *             // the path is either all on one side of the half-plane or the other
 *             if (test < 0) {
 *                 return kAllNegative;
 *             } else {
 *                 return kAllPositive;
 *             }
 *         }
 *         return kMixed;
 *     }
 * }
 * ```
 */
public data class SkHalfPlane public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fA
   * ```
   */
  public var fA: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fA, fB
   * ```
   */
  public var fB: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fA, fB, fC
   * ```
   */
  public var fC: SkScalar,
) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar eval(SkScalar x, SkScalar y) const {
   *         return fA * x + fB * y + fC;
   *     }
   * ```
   */
  public fun eval(x: SkScalar, y: SkScalar): SkScalar {
    TODO("Implement eval")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar operator()(SkScalar x, SkScalar y) const { return this->eval(x, y); }
   * ```
   */
  public operator fun invoke(x: SkScalar, y: SkScalar): SkScalar {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   * bool normalize() {
   *         double a = fA;
   *         double b = fB;
   *         double c = fC;
   *         double dmag = sqrt(a * a + b * b);
   *         // length of initial plane normal is zero
   *         if (dmag == 0) {
   *            fA = fB = 0;
   *            fC = SK_Scalar1;
   *            return true;
   *         }
   *         double dscale = sk_ieee_double_divide(1.0, dmag);
   *         a *= dscale;
   *         b *= dscale;
   *         c *= dscale;
   *         // check if we're not finite, or normal is zero-length
   *         if (!SkIsFinite(a, b, c) ||
   *             (a == 0 && b == 0)) {
   *             fA = fB = 0;
   *             fC = SK_Scalar1;
   *             return false;
   *         }
   *         fA = a;
   *         fB = b;
   *         fC = c;
   *         return true;
   *     }
   * ```
   */
  public fun normalize(): Boolean {
    TODO("Implement normalize")
  }

  /**
   * C++ original:
   * ```cpp
   * Result test(const SkRect& bounds) const {
   *         // check whether the diagonal aligned with the normal crosses the plane
   *         SkPoint diagMin, diagMax;
   *         if (fA >= 0) {
   *             diagMin.fX = bounds.fLeft;
   *             diagMax.fX = bounds.fRight;
   *         } else {
   *             diagMin.fX = bounds.fRight;
   *             diagMax.fX = bounds.fLeft;
   *         }
   *         if (fB >= 0) {
   *             diagMin.fY = bounds.fTop;
   *             diagMax.fY = bounds.fBottom;
   *         } else {
   *             diagMin.fY = bounds.fBottom;
   *             diagMax.fY = bounds.fTop;
   *         }
   *         SkScalar test = this->eval(diagMin.fX, diagMin.fY);
   *         SkScalar sign = test*this->eval(diagMax.fX, diagMax.fY);
   *         if (sign > 0) {
   *             // the path is either all on one side of the half-plane or the other
   *             if (test < 0) {
   *                 return kAllNegative;
   *             } else {
   *                 return kAllPositive;
   *             }
   *         }
   *         return kMixed;
   *     }
   * ```
   */
  public fun test(bounds: SkRect): Result {
    TODO("Implement test")
  }

  public enum class Result {
    kAllNegative,
    kAllPositive,
    kMixed,
  }
}
