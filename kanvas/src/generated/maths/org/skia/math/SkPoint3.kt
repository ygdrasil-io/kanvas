package org.skia.math

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkPoint3 {
 *     SkScalar fX, fY, fZ;
 *
 *     static SkPoint3 Make(SkScalar x, SkScalar y, SkScalar z) {
 *         SkPoint3 pt;
 *         pt.set(x, y, z);
 *         return pt;
 *     }
 *
 *     SkScalar x() const { return fX; }
 *     SkScalar y() const { return fY; }
 *     SkScalar z() const { return fZ; }
 *
 *     void set(SkScalar x, SkScalar y, SkScalar z) { fX = x; fY = y; fZ = z; }
 *
 *     friend bool operator==(const SkPoint3& a, const SkPoint3& b) {
 *         return a.fX == b.fX && a.fY == b.fY && a.fZ == b.fZ;
 *     }
 *
 *     friend bool operator!=(const SkPoint3& a, const SkPoint3& b) {
 *         return !(a == b);
 *     }
 *
 *     /** Returns the Euclidian distance from (0,0,0) to (x,y,z)
 *     */
 *     static SkScalar Length(SkScalar x, SkScalar y, SkScalar z);
 *
 *     /** Return the Euclidian distance from (0,0,0) to the point
 *     */
 *     SkScalar length() const { return SkPoint3::Length(fX, fY, fZ); }
 *
 *     /** Set the point (vector) to be unit-length in the same direction as it
 *         already points.  If the point has a degenerate length (i.e., nearly 0)
 *         then set it to (0,0,0) and return false; otherwise return true.
 *     */
 *     bool normalize();
 *
 *     /** Return a new point whose X, Y and Z coordinates are scaled.
 *     */
 *     SkPoint3 makeScale(SkScalar scale) const {
 *         SkPoint3 p;
 *         p.set(scale * fX, scale * fY, scale * fZ);
 *         return p;
 *     }
 *
 *     /** Scale the point's coordinates by scale.
 *     */
 *     void scale(SkScalar value) {
 *         fX *= value;
 *         fY *= value;
 *         fZ *= value;
 *     }
 *
 *     /** Return a new point whose X, Y and Z coordinates are the negative of the
 *         original point's
 *     */
 *     SkPoint3 operator-() const {
 *         SkPoint3 neg;
 *         neg.fX = -fX;
 *         neg.fY = -fY;
 *         neg.fZ = -fZ;
 *         return neg;
 *     }
 *
 *     /** Returns a new point whose coordinates are the difference between
 *         a and b (i.e., a - b)
 *     */
 *     friend SkPoint3 operator-(const SkPoint3& a, const SkPoint3& b) {
 *         return { a.fX - b.fX, a.fY - b.fY, a.fZ - b.fZ };
 *     }
 *
 *     /** Returns a new point whose coordinates are the sum of a and b (a + b)
 *     */
 *     friend SkPoint3 operator+(const SkPoint3& a, const SkPoint3& b) {
 *         return { a.fX + b.fX, a.fY + b.fY, a.fZ + b.fZ };
 *     }
 *
 *     /** Add v's coordinates to the point's
 *     */
 *     void operator+=(const SkPoint3& v) {
 *         fX += v.fX;
 *         fY += v.fY;
 *         fZ += v.fZ;
 *     }
 *
 *     /** Subtract v's coordinates from the point's
 *     */
 *     void operator-=(const SkPoint3& v) {
 *         fX -= v.fX;
 *         fY -= v.fY;
 *         fZ -= v.fZ;
 *     }
 *
 *     friend SkPoint3 operator*(SkScalar t, SkPoint3 p) {
 *         return { t * p.fX, t * p.fY, t * p.fZ };
 *     }
 *
 *     /** Returns true if fX, fY, and fZ are measurable values.
 *
 *      @return  true for values other than infinities and NaN
 *      */
 *     bool isFinite() const {
 *         return SkIsFinite(fX, fY, fZ);
 *     }
 *
 *     /** Returns the dot product of a and b, treating them as 3D vectors
 *     */
 *     static SkScalar DotProduct(const SkPoint3& a, const SkPoint3& b) {
 *         return a.fX * b.fX + a.fY * b.fY + a.fZ * b.fZ;
 *     }
 *
 *     SkScalar dot(const SkPoint3& vec) const {
 *         return DotProduct(*this, vec);
 *     }
 *
 *     /** Returns the cross product of a and b, treating them as 3D vectors
 *     */
 *     static SkPoint3 CrossProduct(const SkPoint3& a, const SkPoint3& b) {
 *         SkPoint3 result;
 *         result.fX = a.fY*b.fZ - a.fZ*b.fY;
 *         result.fY = a.fZ*b.fX - a.fX*b.fZ;
 *         result.fZ = a.fX*b.fY - a.fY*b.fX;
 *
 *         return result;
 *     }
 *
 *     SkPoint3 cross(const SkPoint3& vec) const {
 *         return CrossProduct(*this, vec);
 *     }
 * }
 * ```
 */
public open class SkPoint3 public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fX
   * ```
   */
  public var fX: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fX, fY
   * ```
   */
  public var fY: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fX, fY, fZ
   * ```
   */
  public var fZ: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkScalar x() const { return fX; }
   * ```
   */
  public fun x(): Int {
    return fX
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar y() const { return fY; }
   * ```
   */
  public fun y(): Int {
    TODO("Implement y")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar z() const { return fZ; }
   * ```
   */
  public fun z(): Int {
    return fZ
  }

  /**
   * C++ original:
   * ```cpp
   * void set(SkScalar x, SkScalar y, SkScalar z) { fX = x; fY = y; fZ = z; }
   * ```
   */
  public fun `set`(
    x: SkScalar,
    y: SkScalar,
    z: SkScalar,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar length() const { return SkPoint3::Length(fX, fY, fZ); }
   * ```
   */
  public fun length(): Int {
    TODO("Implement length")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPoint3::normalize() {
   *     float magSq;
   *     if (is_length_nearly_zero(fX, fY, fZ, &magSq)) {
   *         this->set(0, 0, 0);
   *         return false;
   *     }
   *     // sqrtf does not provide enough precision; since sqrt takes a double,
   *     // there's no additional penalty to storing invScale in a double
   *     double invScale;
   *     if (SkIsFinite(magSq)) {
   *         invScale = magSq;
   *     } else {
   *         // our magSq step overflowed to infinity, so use doubles instead.
   *         // much slower, but needed when x, y or z is very large, otherwise we
   *         // divide by inf. and return (0,0,0) vector.
   *         double xx = fX;
   *         double yy = fY;
   *         double zz = fZ;
   *         invScale = xx * xx + yy * yy + zz * zz;
   *     }
   *     // using a float instead of a double for scale loses too much precision
   *     double scale = 1 / sqrt(invScale);
   *     fX *= scale;
   *     fY *= scale;
   *     fZ *= scale;
   *     if (!SkIsFinite(fX, fY, fZ)) {
   *         this->set(0, 0, 0);
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  public fun normalize(): Boolean {
    TODO("Implement normalize")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint3 makeScale(SkScalar scale) const {
   *         SkPoint3 p;
   *         p.set(scale * fX, scale * fY, scale * fZ);
   *         return p;
   *     }
   * ```
   */
  public fun makeScale(scale: SkScalar): SkPoint3 {
    return Companion.make(scale * fX, scale * fY, scale * fZ)
  }

  /**
   * C++ original:
   * ```cpp
   * void scale(SkScalar value) {
   *         fX *= value;
   *         fY *= value;
   *         fZ *= value;
   *     }
   * ```
   */
  public fun scale(`value`: SkScalar) {
    TODO("Implement scale")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint3 operator-() const {
   *         SkPoint3 neg;
   *         neg.fX = -fX;
   *         neg.fY = -fY;
   *         neg.fZ = -fZ;
   *         return neg;
   *     }
   * ```
   */
  public operator fun unaryMinus(): SkPoint3 {
    return SkPoint3(fX = -this.fX, fY = -this.fY, fZ = -this.fZ)
  }

  /**
   * C++ original:
   * ```cpp
   * void operator+=(const SkPoint3& v) {
   *         fX += v.fX;
   *         fY += v.fY;
   *         fZ += v.fZ;
   *     }
   * ```
   */
  public operator fun plusAssign(v: SkPoint3) {
    this.fX += v.fX
    this.fY += v.fY
    this.fZ += v.fZ
  }

  /**
   * C++ original:
   * ```cpp
   * void operator-=(const SkPoint3& v) {
   *         fX -= v.fX;
   *         fY -= v.fY;
   *         fZ -= v.fZ;
   *     }
   * ```
   */
  public operator fun minusAssign(v: SkPoint3) {
    fX -= v.fX
    fY -= v.fY
    fZ -= v.fZ
  }

  /**
   * C++ original:
   * ```cpp
   * bool isFinite() const {
   *         return SkIsFinite(fX, fY, fZ);
   *     }
   * ```
   */
  public fun isFinite(): Boolean {
    TODO("Implement isFinite")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar dot(const SkPoint3& vec) const {
   *         return DotProduct(*this, vec);
   *     }
   * ```
   */
  public fun dot(vec: SkPoint3): Int {
    TODO("Implement dot")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint3 cross(const SkPoint3& vec) const {
   *         return CrossProduct(*this, vec);
   *     }
   * ```
   */
  public fun cross(vec: SkPoint3): SkPoint3 {
    TODO("Implement cross")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static SkPoint3 Make(SkScalar x, SkScalar y, SkScalar z) {
     *         SkPoint3 pt;
     *         pt.set(x, y, z);
     *         return pt;
     *     }
     * ```
     */
    public fun make(
      x: SkScalar,
      y: SkScalar,
      z: SkScalar,
    ): SkPoint3 {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkScalar SkPoint3::Length(SkScalar x, SkScalar y, SkScalar z) {
     *     float magSq = get_length_squared(x, y, z);
     *     if (SkIsFinite(magSq)) {
     *         return std::sqrt(magSq);
     *     } else {
     *         double xx = x;
     *         double yy = y;
     *         double zz = z;
     *         return (float)sqrt(xx * xx + yy * yy + zz * zz);
     *     }
     * }
     * ```
     */
    public fun length(
      x: SkScalar,
      y: SkScalar,
      z: SkScalar,
    ): Int {
      TODO("Implement length")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkScalar DotProduct(const SkPoint3& a, const SkPoint3& b) {
     *         return a.fX * b.fX + a.fY * b.fY + a.fZ * b.fZ;
     *     }
     * ```
     */
    public fun dotProduct(a: SkPoint3, b: SkPoint3): Int {
      TODO("Implement dotProduct")
    }

    /**
     * C++ original:
     * ```cpp
     * static SkPoint3 CrossProduct(const SkPoint3& a, const SkPoint3& b) {
     *         SkPoint3 result;
     *         result.fX = a.fY*b.fZ - a.fZ*b.fY;
     *         result.fY = a.fZ*b.fX - a.fX*b.fZ;
     *         result.fZ = a.fX*b.fY - a.fY*b.fX;
     *
     *         return result;
     *     }
     * ```
     */
    public fun crossProduct(a: SkPoint3, b: SkPoint3): SkPoint3 {
      TODO("Implement crossProduct")
    }
  }
}

public typealias SkVector3 = SkPoint3

public typealias SkColor3f = SkPoint3
