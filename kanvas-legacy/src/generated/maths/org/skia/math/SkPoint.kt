package org.skia.math

import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int

public typealias SkVector = SkPoint

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkPoint {
 *     float fX; //!< x-axis value
 *     float fY; //!< y-axis value
 *
 *     /** Sets fX to x, fY to y. Used both to set SkPoint and vector.
 *
 *         @param x  float x-axis value of constructed SkPoint or vector
 *         @param y  float y-axis value of constructed SkPoint or vector
 *         @return   SkPoint (x, y)
 *     */
 *     static constexpr SkPoint Make(float x, float y) {
 *         return {x, y};
 *     }
 *
 *     /** Returns x-axis value of SkPoint or vector.
 *
 *         @return  fX
 *     */
 *     constexpr float x() const { return fX; }
 *
 *     /** Returns y-axis value of SkPoint or vector.
 *
 *         @return  fY
 *     */
 *     constexpr float y() const { return fY; }
 *
 *     /** Returns true if fX and fY are both zero.
 *
 *         @return  true if fX is zero and fY is zero
 *     */
 *     bool isZero() const { return (0 == fX) & (0 == fY); }
 *
 *     /** Sets fX to x and fY to y.
 *
 *         @param x  new value for fX
 *         @param y  new value for fY
 *     */
 *     void set(float x, float y) {
 *         fX = x;
 *         fY = y;
 *     }
 *
 *     /** Sets fX to x and fY to y, promoting integers to float values.
 *
 *         Assigning a large integer value directly to fX or fY may cause a compiler
 *         error, triggered by narrowing conversion of int to float. This safely
 *         casts x and y to avoid the error.
 *
 *         @param x  new value for fX
 *         @param y  new value for fY
 *     */
 *     void iset(int32_t x, int32_t y) {
 *         fX = static_cast<float>(x);
 *         fY = static_cast<float>(y);
 *     }
 *
 *     /** Sets fX to p.fX and fY to p.fY, promoting integers to float values.
 *
 *         Assigning an SkIPoint containing a large integer value directly to fX or fY may
 *         cause a compiler error, triggered by narrowing conversion of int to float.
 *         This safely casts p.fX and p.fY to avoid the error.
 *
 *         @param p  SkIPoint members promoted to float
 *     */
 *     void iset(const SkIPoint& p) {
 *         fX = static_cast<float>(p.fX);
 *         fY = static_cast<float>(p.fY);
 *     }
 *
 *     /** Sets fX to absolute value of pt.fX; and fY to absolute value of pt.fY.
 *
 *         @param pt  members providing magnitude for fX and fY
 *     */
 *     void setAbs(const SkPoint& pt) {
 *         fX = std::abs(pt.fX);
 *         fY = std::abs(pt.fY);
 *     }
 *
 *     /** Adds offset to each SkPoint in points array with count entries.
 *
 *         @param points  SkPoint array
 *         @param count   entries in array
 *         @param offset  vector added to points
 *     */
 *     static void Offset(SkPoint points[], int count, const SkVector& offset) {
 *         Offset(points, count, offset.fX, offset.fY);
 *     }
 *
 *     /** Adds offset (dx, dy) to each SkPoint in points array of length count.
 *
 *         @param points  SkPoint array
 *         @param count   entries in array
 *         @param dx      added to fX in points
 *         @param dy      added to fY in points
 *     */
 *     static void Offset(SkPoint points[], int count, float dx, float dy) {
 *         for (int i = 0; i < count; ++i) {
 *             points[i].offset(dx, dy);
 *         }
 *     }
 *
 *     /** Adds offset (dx, dy) to SkPoint.
 *
 *         @param dx  added to fX
 *         @param dy  added to fY
 *     */
 *     void offset(float dx, float dy) {
 *         fX += dx;
 *         fY += dy;
 *     }
 *
 *     /** Returns the Euclidean distance from origin, computed as:
 *
 *             sqrt(fX * fX + fY * fY)
 *
 *         .
 *
 *         @return  straight-line distance to origin
 *     */
 *     float length() const { return SkPoint::Length(fX, fY); }
 *
 *     /** Returns the Euclidean distance from origin, computed as:
 *
 *             sqrt(fX * fX + fY * fY)
 *
 *         .
 *
 *         @return  straight-line distance to origin
 *     */
 *     float distanceToOrigin() const { return this->length(); }
 *
 *     /** Scales (fX, fY) so that length() returns one, while preserving ratio of fX to fY,
 *         if possible. If prior length is nearly zero, sets vector to (0, 0) and returns
 *         false; otherwise returns true.
 *
 *         @return  true if former length is not zero or nearly zero
 *
 *         example: https://fiddle.skia.org/c/@Point_normalize_2
 *     */
 *     bool normalize();
 *
 *     /** Sets vector to (x, y) scaled so length() returns one, and so that
 *         (fX, fY) is proportional to (x, y).  If (x, y) length is nearly zero,
 *         sets vector to (0, 0) and returns false; otherwise returns true.
 *
 *         @param x  proportional value for fX
 *         @param y  proportional value for fY
 *         @return   true if (x, y) length is not zero or nearly zero
 *
 *         example: https://fiddle.skia.org/c/@Point_setNormalize
 *     */
 *     bool setNormalize(float x, float y);
 *
 *     /** Scales vector so that distanceToOrigin() returns length, if possible. If former
 *         length is nearly zero, sets vector to (0, 0) and return false; otherwise returns
 *         true.
 *
 *         @param length  straight-line distance to origin
 *         @return        true if former length is not zero or nearly zero
 *
 *         example: https://fiddle.skia.org/c/@Point_setLength
 *     */
 *     bool setLength(float length);
 *
 *     /** Sets vector to (x, y) scaled to length, if possible. If former
 *         length is nearly zero, sets vector to (0, 0) and return false; otherwise returns
 *         true.
 *
 *         @param x       proportional value for fX
 *         @param y       proportional value for fY
 *         @param length  straight-line distance to origin
 *         @return        true if (x, y) length is not zero or nearly zero
 *
 *         example: https://fiddle.skia.org/c/@Point_setLength_2
 *     */
 *     bool setLength(float x, float y, float length);
 *
 *     /** Sets dst to SkPoint times scale. dst may be SkPoint to modify SkPoint in place.
 *
 *         @param scale  factor to multiply SkPoint by
 *         @param dst    storage for scaled SkPoint
 *
 *         example: https://fiddle.skia.org/c/@Point_scale
 *     */
 *     void scale(float scale, SkPoint* dst) const;
 *
 *     /** Scales SkPoint in place by scale.
 *
 *         @param value  factor to multiply SkPoint by
 *     */
 *     void scale(float value) { this->scale(value, this); }
 *
 *     /** Changes the sign of fX and fY.
 *     */
 *     void negate() {
 *         fX = -fX;
 *         fY = -fY;
 *     }
 *
 *     /** Returns SkPoint changing the signs of fX and fY.
 *
 *         @return  SkPoint as (-fX, -fY)
 *     */
 *     SkPoint operator-() const {
 *         return {-fX, -fY};
 *     }
 *
 *     /** Adds vector v to SkPoint. Sets SkPoint to: (fX + v.fX, fY + v.fY).
 *
 *         @param v  vector to add
 *     */
 *     void operator+=(const SkVector& v) {
 *         fX += v.fX;
 *         fY += v.fY;
 *     }
 *
 *     /** Subtracts vector v from SkPoint. Sets SkPoint to: (fX - v.fX, fY - v.fY).
 *
 *         @param v  vector to subtract
 *     */
 *     void operator-=(const SkVector& v) {
 *         fX -= v.fX;
 *         fY -= v.fY;
 *     }
 *
 *     /** Returns SkPoint multiplied by scale.
 *
 *         @param scale  float to multiply by
 *         @return       SkPoint as (fX * scale, fY * scale)
 *     */
 *     SkPoint operator*(float scale) const {
 *         return {fX * scale, fY * scale};
 *     }
 *
 *     /** Multiplies SkPoint by scale. Sets SkPoint to: (fX * scale, fY * scale).
 *
 *         @param scale  float to multiply by
 *         @return       reference to SkPoint
 *     */
 *     SkPoint& operator*=(float scale) {
 *         fX *= scale;
 *         fY *= scale;
 *         return *this;
 *     }
 *
 *     /** Returns true if both fX and fY are measurable values.
 *
 *         @return  true for values other than infinities and NaN
 *     */
 *     bool isFinite() const {
 *         return SkIsFinite(fX, fY);
 *     }
 *
 *     /** Returns true if SkPoint is equivalent to SkPoint constructed from (x, y).
 *
 *         @param x  value compared with fX
 *         @param y  value compared with fY
 *         @return   true if SkPoint equals (x, y)
 *     */
 *     bool equals(float x, float y) const {
 *         return fX == x && fY == y;
 *     }
 *
 *     /** Returns true if a is equivalent to b.
 *
 *         @param a  SkPoint to compare
 *         @param b  SkPoint to compare
 *         @return   true if a.fX == b.fX and a.fY == b.fY
 *     */
 *     friend bool operator==(const SkPoint& a, const SkPoint& b) {
 *         return a.fX == b.fX && a.fY == b.fY;
 *     }
 *
 *     /** Returns true if a is not equivalent to b.
 *
 *         @param a  SkPoint to compare
 *         @param b  SkPoint to compare
 *         @return   true if a.fX != b.fX or a.fY != b.fY
 *     */
 *     friend bool operator!=(const SkPoint& a, const SkPoint& b) {
 *         return a.fX != b.fX || a.fY != b.fY;
 *     }
 *
 *     /** Returns vector from b to a, computed as (a.fX - b.fX, a.fY - b.fY).
 *
 *         Can also be used to subtract vector from SkPoint, returning SkPoint.
 *         Can also be used to subtract vector from vector, returning vector.
 *
 *         @param a  SkPoint to subtract from
 *         @param b  SkPoint to subtract
 *         @return   vector from b to a
 *     */
 *     friend SkVector operator-(const SkPoint& a, const SkPoint& b) {
 *         return {a.fX - b.fX, a.fY - b.fY};
 *     }
 *
 *     /** Returns SkPoint resulting from SkPoint a offset by vector b, computed as:
 *         (a.fX + b.fX, a.fY + b.fY).
 *
 *         Can also be used to offset SkPoint b by vector a, returning SkPoint.
 *         Can also be used to add vector to vector, returning vector.
 *
 *         @param a  SkPoint or vector to add to
 *         @param b  SkPoint or vector to add
 *         @return   SkPoint equal to a offset by b
 *     */
 *     friend SkPoint operator+(const SkPoint& a, const SkVector& b) {
 *         return {a.fX + b.fX, a.fY + b.fY};
 *     }
 *
 *     /** Returns the Euclidean distance from origin, computed as:
 *
 *             sqrt(x * x + y * y)
 *
 *         .
 *
 *         @param x  component of length
 *         @param y  component of length
 *         @return   straight-line distance to origin
 *
 *         example: https://fiddle.skia.org/c/@Point_Length
 *     */
 *     static float Length(float x, float y);
 *
 *     /** Scales (vec->fX, vec->fY) so that length() returns one, while preserving ratio of vec->fX
 *         to vec->fY, if possible. If original length is nearly zero, sets vec to (0, 0) and returns
 *         zero; otherwise, returns length of vec before vec is scaled.
 *
 *         Returned prior length may be INFINITY if it can not be represented by float.
 *
 *         Note that normalize() is faster if prior length is not required.
 *
 *         @param vec  normalized to unit length
 *         @return     original vec length
 *
 *         example: https://fiddle.skia.org/c/@Point_Normalize
 *     */
 *     static float Normalize(SkVector* vec);
 *
 *     /** Returns the Euclidean distance between a and b.
 *
 *         @param a  line end point
 *         @param b  line end point
 *         @return   straight-line distance from a to b
 *     */
 *     static float Distance(const SkPoint& a, const SkPoint& b) {
 *         return Length(a.fX - b.fX, a.fY - b.fY);
 *     }
 *
 *     /** Returns the dot product of vector a and vector b.
 *
 *         @param a  left side of dot product
 *         @param b  right side of dot product
 *         @return   product of input magnitudes and cosine of the angle between them
 *     */
 *     static float DotProduct(const SkVector& a, const SkVector& b) {
 *         return a.fX * b.fX + a.fY * b.fY;
 *     }
 *
 *     /** Returns the cross product of vector a and vector b.
 *
 *         a and b form three-dimensional vectors with z-axis value equal to zero. The
 *         cross product is a three-dimensional vector with x-axis and y-axis values equal
 *         to zero. The cross product z-axis component is returned.
 *
 *         @param a  left side of cross product
 *         @param b  right side of cross product
 *         @return   area spanned by vectors signed by angle direction
 *     */
 *     static float CrossProduct(const SkVector& a, const SkVector& b) {
 *         return a.fX * b.fY - a.fY * b.fX;
 *     }
 *
 *     /** Returns the cross product of vector and vec.
 *
 *         Vector and vec form three-dimensional vectors with z-axis value equal to zero.
 *         The cross product is a three-dimensional vector with x-axis and y-axis values
 *         equal to zero. The cross product z-axis component is returned.
 *
 *         @param vec  right side of cross product
 *         @return     area spanned by vectors signed by angle direction
 *     */
 *     float cross(const SkVector& vec) const {
 *         return CrossProduct(*this, vec);
 *     }
 *
 *     /** Returns the dot product of vector and vector vec.
 *
 *         @param vec  right side of dot product
 *         @return     product of input magnitudes and cosine of the angle between them
 *     */
 *     float dot(const SkVector& vec) const {
 *         return DotProduct(*this, vec);
 *     }
 *
 * }
 * ```
 */
public open class SkPoint public constructor(
  /**
   * C++ original:
   * ```cpp
   * float fX
   * ```
   */
  public var fX: Float,
  /**
   * C++ original:
   * ```cpp
   * float fY
   * ```
   */
  public var fY: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr float x() const { return fX; }
   * ```
   */
  public fun x(): Float {
    TODO("Implement x")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr float y() const { return fY; }
   * ```
   */
  public fun y(): Float {
    TODO("Implement y")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isZero() const { return (0 == fX) & (0 == fY); }
   * ```
   */
  public fun isZero(): Boolean {
    TODO("Implement isZero")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(float x, float y) {
   *         fX = x;
   *         fY = y;
   *     }
   * ```
   */
  public fun `set`(x: Float, y: Float) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void iset(int32_t x, int32_t y) {
   *         fX = static_cast<float>(x);
   *         fY = static_cast<float>(y);
   *     }
   * ```
   */
  public fun iset(x: Int, y: Int) {
    TODO("Implement iset")
  }

  /**
   * C++ original:
   * ```cpp
   * void iset(const SkIPoint& p) {
   *         fX = static_cast<float>(p.fX);
   *         fY = static_cast<float>(p.fY);
   *     }
   * ```
   */
  public fun iset(p: SkIPoint) {
    TODO("Implement iset")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAbs(const SkPoint& pt) {
   *         fX = std::abs(pt.fX);
   *         fY = std::abs(pt.fY);
   *     }
   * ```
   */
  public fun setAbs(pt: SkPoint) {
    TODO("Implement setAbs")
  }

  /**
   * C++ original:
   * ```cpp
   * void offset(float dx, float dy) {
   *         fX += dx;
   *         fY += dy;
   *     }
   * ```
   */
  public fun offset(dx: Float, dy: Float) {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * float length() const { return SkPoint::Length(fX, fY); }
   * ```
   */
  public fun length(): Float {
    TODO("Implement length")
  }

  /**
   * C++ original:
   * ```cpp
   * float distanceToOrigin() const { return this->length(); }
   * ```
   */
  public fun distanceToOrigin(): Float {
    TODO("Implement distanceToOrigin")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPoint::normalize() {
   *     return this->setLength(fX, fY, 1);
   * }
   * ```
   */
  public fun normalize(): Boolean {
    TODO("Implement normalize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPoint::setNormalize(float x, float y) {
   *     return this->setLength(x, y, 1);
   * }
   * ```
   */
  public fun setNormalize(x: Float, y: Float): Boolean {
    TODO("Implement setNormalize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPoint::setLength(float length) {
   *     return this->setLength(fX, fY, length);
   * }
   * ```
   */
  public fun setLength(length: Float): Boolean {
    TODO("Implement setLength")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkPoint::setLength(float x, float y, float length) {
   *     return set_point_length<false>(this, x, y, length);
   * }
   * ```
   */
  public fun setLength(
    x: Float,
    y: Float,
    length: Float,
  ): Boolean {
    TODO("Implement setLength")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPoint::scale(float scale, SkPoint* dst) const {
   *     SkASSERT(dst);
   *     dst->set(fX * scale, fY * scale);
   * }
   * ```
   */
  public fun scale(scale: Float, dst: SkPoint?) {
    TODO("Implement scale")
  }

  /**
   * C++ original:
   * ```cpp
   * void scale(float value) { this->scale(value, this); }
   * ```
   */
  public fun scale(`value`: Float) {
    TODO("Implement scale")
  }

  /**
   * C++ original:
   * ```cpp
   * void negate() {
   *         fX = -fX;
   *         fY = -fY;
   *     }
   * ```
   */
  public fun negate() {
    TODO("Implement negate")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint operator-() const {
   *         return {-fX, -fY};
   *     }
   * ```
   */
  public operator fun unaryMinus(): SkPoint {
    TODO("Implement unaryMinus")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator+=(const SkVector& v) {
   *         fX += v.fX;
   *         fY += v.fY;
   *     }
   * ```
   */
  public operator fun plusAssign(v: SkVector) {
    TODO("Implement plusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator-=(const SkVector& v) {
   *         fX -= v.fX;
   *         fY -= v.fY;
   *     }
   * ```
   */
  public operator fun minusAssign(v: SkVector) {
    TODO("Implement minusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint operator*(float scale) const {
   *         return {fX * scale, fY * scale};
   *     }
   * ```
   */
  public operator fun times(scale: Float): SkPoint {
    TODO("Implement times")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint& operator*=(float scale) {
   *         fX *= scale;
   *         fY *= scale;
   *         return *this;
   *     }
   * ```
   */
  public operator fun timesAssign(scale: Float) {
    TODO("Implement timesAssign")
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

  /**
   * C++ original:
   * ```cpp
   * bool equals(float x, float y) const {
   *         return fX == x && fY == y;
   *     }
   * ```
   */
  public override fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * float cross(const SkVector& vec) const {
   *         return CrossProduct(*this, vec);
   *     }
   * ```
   */
  public fun cross(vec: SkVector): Float {
    TODO("Implement cross")
  }

  /**
   * C++ original:
   * ```cpp
   * float dot(const SkVector& vec) const {
   *         return DotProduct(*this, vec);
   *     }
   * ```
   */
  public fun dot(vec: SkVector): Float {
    TODO("Implement dot")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkPoint Make(float x, float y) {
     *         return {x, y};
     *     }
     * ```
     */
    public fun make(x: Float, y: Float): SkPoint {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static void Offset(SkPoint points[], int count, const SkVector& offset) {
     *         Offset(points, count, offset.fX, offset.fY);
     *     }
     * ```
     */
    public fun offset(
      points: Array<SkPoint>,
      count: Int,
      offset: SkVector,
    ) {
      TODO("Implement offset")
    }

    /**
     * C++ original:
     * ```cpp
     * static void Offset(SkPoint points[], int count, float dx, float dy) {
     *         for (int i = 0; i < count; ++i) {
     *             points[i].offset(dx, dy);
     *         }
     *     }
     * ```
     */
    public fun offset(
      points: Array<SkPoint>,
      count: Int,
      dx: Float,
      dy: Float,
    ) {
      TODO("Implement offset")
    }

    /**
     * C++ original:
     * ```cpp
     * float SkPoint::Length(float dx, float dy) {
     *     float mag2 = dx * dx + dy * dy;
     *     if (SkIsFinite(mag2)) {
     *         return std::sqrt(mag2);
     *     } else {
     *         double xx = dx;
     *         double yy = dy;
     *         return sk_double_to_float(sqrt(xx * xx + yy * yy));
     *     }
     * }
     * ```
     */
    public fun length(x: Float, y: Float): Float {
      TODO("Implement length")
    }

    /**
     * C++ original:
     * ```cpp
     * float SkPoint::Normalize(SkPoint* pt) {
     *     float mag;
     *     if (set_point_length<false>(pt, pt->fX, pt->fY, 1.0f, &mag)) {
     *         return mag;
     *     }
     *     return 0;
     * }
     * ```
     */
    public fun normalize(vec: SkVector?): Float {
      TODO("Implement normalize")
    }

    /**
     * C++ original:
     * ```cpp
     * static float Distance(const SkPoint& a, const SkPoint& b) {
     *         return Length(a.fX - b.fX, a.fY - b.fY);
     *     }
     * ```
     */
    public fun distance(a: SkPoint, b: SkPoint): Float {
      TODO("Implement distance")
    }

    /**
     * C++ original:
     * ```cpp
     * static float DotProduct(const SkVector& a, const SkVector& b) {
     *         return a.fX * b.fX + a.fY * b.fY;
     *     }
     * ```
     */
    public fun dotProduct(a: SkVector, b: SkVector): Float {
      TODO("Implement dotProduct")
    }

    /**
     * C++ original:
     * ```cpp
     * static float CrossProduct(const SkVector& a, const SkVector& b) {
     *         return a.fX * b.fY - a.fY * b.fX;
     *     }
     * ```
     */
    public fun crossProduct(a: SkVector, b: SkVector): Float {
      TODO("Implement crossProduct")
    }
  }
}
