package org.skia.core

import kotlin.Boolean
import kotlin.Double
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct SkDVector {
 *     double fX;
 *     double fY;
 *
 *     SkDVector& set(const SkVector& pt) {
 *         fX = pt.fX;
 *         fY = pt.fY;
 *         return *this;
 *     }
 *
 *     // only used by testing
 *     void operator+=(const SkDVector& v) {
 *         fX += v.fX;
 *         fY += v.fY;
 *     }
 *
 *     // only called by nearestT, which is currently only used by testing
 *     void operator-=(const SkDVector& v) {
 *         fX -= v.fX;
 *         fY -= v.fY;
 *     }
 *
 *     // only used by testing
 *     void operator/=(const double s) {
 *         fX /= s;
 *         fY /= s;
 *     }
 *
 *     // only used by testing
 *     void operator*=(const double s) {
 *         fX *= s;
 *         fY *= s;
 *     }
 *
 *     SkVector asSkVector() const {
 *         SkVector v = {SkDoubleToScalar(fX), SkDoubleToScalar(fY)};
 *         return v;
 *     }
 *
 *     // only used by testing
 *     double cross(const SkDVector& a) const {
 *         return fX * a.fY - fY * a.fX;
 *     }
 *
 *     // similar to cross, this bastardization considers nearly coincident to be zero
 *     // uses ulps epsilon == 16
 *     double crossCheck(const SkDVector& a) const {
 *         double xy = fX * a.fY;
 *         double yx = fY * a.fX;
 *         return AlmostEqualUlps(xy, yx) ? 0 : xy - yx;
 *     }
 *
 *     // allow tinier numbers
 *     double crossNoNormalCheck(const SkDVector& a) const {
 *         double xy = fX * a.fY;
 *         double yx = fY * a.fX;
 *         return AlmostEqualUlpsNoNormalCheck(xy, yx) ? 0 : xy - yx;
 *     }
 *
 *     double dot(const SkDVector& a) const {
 *         return fX * a.fX + fY * a.fY;
 *     }
 *
 *     double length() const {
 *         return sqrt(lengthSquared());
 *     }
 *
 *     double lengthSquared() const {
 *         return fX * fX + fY * fY;
 *     }
 *
 *     SkDVector& normalize() {
 *         double inverseLength = sk_ieee_double_divide(1, this->length());
 *         fX *= inverseLength;
 *         fY *= inverseLength;
 *         return *this;
 *     }
 *
 *     bool isFinite() const {
 *         return SkIsFinite(fX, fY);
 *     }
 * }
 * ```
 */
public data class SkDVector public constructor(
  /**
   * C++ original:
   * ```cpp
   * double fX
   * ```
   */
  public var fX: Double,
  /**
   * C++ original:
   * ```cpp
   * double fY
   * ```
   */
  public var fY: Double,
) {
  /**
   * C++ original:
   * ```cpp
   * SkDVector& set(const SkVector& pt) {
   *         fX = pt.fX;
   *         fY = pt.fY;
   *         return *this;
   *     }
   * ```
   */
  public fun `set`(pt: SkVector): SkDVector {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator+=(const SkDVector& v) {
   *         fX += v.fX;
   *         fY += v.fY;
   *     }
   * ```
   */
  public operator fun plusAssign(v: SkDVector) {
    TODO("Implement plusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator-=(const SkDVector& v) {
   *         fX -= v.fX;
   *         fY -= v.fY;
   *     }
   * ```
   */
  public operator fun minusAssign(v: SkDVector) {
    TODO("Implement minusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator/=(const double s) {
   *         fX /= s;
   *         fY /= s;
   *     }
   * ```
   */
  public operator fun divAssign(s: Double) {
    TODO("Implement divAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator*=(const double s) {
   *         fX *= s;
   *         fY *= s;
   *     }
   * ```
   */
  public operator fun timesAssign(s: Double) {
    TODO("Implement timesAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkVector asSkVector() const {
   *         SkVector v = {SkDoubleToScalar(fX), SkDoubleToScalar(fY)};
   *         return v;
   *     }
   * ```
   */
  public fun asSkVector(): SkVector {
    TODO("Implement asSkVector")
  }

  /**
   * C++ original:
   * ```cpp
   * double cross(const SkDVector& a) const {
   *         return fX * a.fY - fY * a.fX;
   *     }
   * ```
   */
  public fun cross(a: SkDVector): Double {
    TODO("Implement cross")
  }

  /**
   * C++ original:
   * ```cpp
   * double crossCheck(const SkDVector& a) const {
   *         double xy = fX * a.fY;
   *         double yx = fY * a.fX;
   *         return AlmostEqualUlps(xy, yx) ? 0 : xy - yx;
   *     }
   * ```
   */
  public fun crossCheck(a: SkDVector): Double {
    TODO("Implement crossCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * double crossNoNormalCheck(const SkDVector& a) const {
   *         double xy = fX * a.fY;
   *         double yx = fY * a.fX;
   *         return AlmostEqualUlpsNoNormalCheck(xy, yx) ? 0 : xy - yx;
   *     }
   * ```
   */
  public fun crossNoNormalCheck(a: SkDVector): Double {
    TODO("Implement crossNoNormalCheck")
  }

  /**
   * C++ original:
   * ```cpp
   * double dot(const SkDVector& a) const {
   *         return fX * a.fX + fY * a.fY;
   *     }
   * ```
   */
  public fun dot(a: SkDVector): Double {
    TODO("Implement dot")
  }

  /**
   * C++ original:
   * ```cpp
   * double length() const {
   *         return sqrt(lengthSquared());
   *     }
   * ```
   */
  public fun length(): Double {
    TODO("Implement length")
  }

  /**
   * C++ original:
   * ```cpp
   * double lengthSquared() const {
   *         return fX * fX + fY * fY;
   *     }
   * ```
   */
  public fun lengthSquared(): Double {
    TODO("Implement lengthSquared")
  }

  /**
   * C++ original:
   * ```cpp
   * SkDVector& normalize() {
   *         double inverseLength = sk_ieee_double_divide(1, this->length());
   *         fX *= inverseLength;
   *         fY *= inverseLength;
   *         return *this;
   *     }
   * ```
   */
  public fun normalize(): SkDVector {
    TODO("Implement normalize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFinite() const {
   *         return SkIsFinite(fX, fY);
   *     }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }
}
