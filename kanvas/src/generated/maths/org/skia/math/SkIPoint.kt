package org.skia.math

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

public typealias SkIVector = SkIPoint

/**
 * C++ original:
 * ```cpp
 * struct SkIPoint {
 *     int32_t fX; //!< x-axis value
 *     int32_t fY; //!< y-axis value
 *
 *     /** Sets fX to x, fY to y.
 *
 *         @param x  integer x-axis value of constructed SkIPoint
 *         @param y  integer y-axis value of constructed SkIPoint
 *         @return   SkIPoint (x, y)
 *     */
 *     static constexpr SkIPoint Make(int32_t x, int32_t y) {
 *         return {x, y};
 *     }
 *
 *     /** Returns x-axis value of SkIPoint.
 *
 *         @return  fX
 *     */
 *     constexpr int32_t x() const { return fX; }
 *
 *     /** Returns y-axis value of SkIPoint.
 *
 *         @return  fY
 *     */
 *     constexpr int32_t y() const { return fY; }
 *
 *     /** Returns true if fX and fY are both zero.
 *
 *         @return  true if fX is zero and fY is zero
 *     */
 *     bool isZero() const { return (fX | fY) == 0; }
 *
 *     /** Sets fX to x and fY to y.
 *
 *         @param x  new value for fX
 *         @param y  new value for fY
 *     */
 *     void set(int32_t x, int32_t y) {
 *         fX = x;
 *         fY = y;
 *     }
 *
 *     /** Returns SkIPoint changing the signs of fX and fY.
 *
 *         @return  SkIPoint as (-fX, -fY)
 *     */
 *     SkIPoint operator-() const {
 *         return {-fX, -fY};
 *     }
 *
 *     /** Offsets SkIPoint by ivector v. Sets SkIPoint to (fX + v.fX, fY + v.fY).
 *
 *         @param v  ivector to add
 *     */
 *     void operator+=(const SkIVector& v) {
 *         fX = Sk32_sat_add(fX, v.fX);
 *         fY = Sk32_sat_add(fY, v.fY);
 *     }
 *
 *     /** Subtracts ivector v from SkIPoint. Sets SkIPoint to: (fX - v.fX, fY - v.fY).
 *
 *         @param v  ivector to subtract
 *     */
 *     void operator-=(const SkIVector& v) {
 *         fX = Sk32_sat_sub(fX, v.fX);
 *         fY = Sk32_sat_sub(fY, v.fY);
 *     }
 *
 *     /** Returns true if SkIPoint is equivalent to SkIPoint constructed from (x, y).
 *
 *         @param x  value compared with fX
 *         @param y  value compared with fY
 *         @return   true if SkIPoint equals (x, y)
 *     */
 *     bool equals(int32_t x, int32_t y) const {
 *         return fX == x && fY == y;
 *     }
 *
 *     /** Returns true if a is equivalent to b.
 *
 *         @param a  SkIPoint to compare
 *         @param b  SkIPoint to compare
 *         @return   true if a.fX == b.fX and a.fY == b.fY
 *     */
 *     friend bool operator==(const SkIPoint& a, const SkIPoint& b) {
 *         return a.fX == b.fX && a.fY == b.fY;
 *     }
 *
 *     /** Returns true if a is not equivalent to b.
 *
 *         @param a  SkIPoint to compare
 *         @param b  SkIPoint to compare
 *         @return   true if a.fX != b.fX or a.fY != b.fY
 *     */
 *     friend bool operator!=(const SkIPoint& a, const SkIPoint& b) {
 *         return a.fX != b.fX || a.fY != b.fY;
 *     }
 *
 *     /** Returns ivector from b to a; computed as (a.fX - b.fX, a.fY - b.fY).
 *
 *         Can also be used to subtract ivector from ivector, returning ivector.
 *
 *         @param a  SkIPoint or ivector to subtract from
 *         @param b  ivector to subtract
 *         @return   ivector from b to a
 *     */
 *     friend SkIVector operator-(const SkIPoint& a, const SkIPoint& b) {
 *         return { Sk32_sat_sub(a.fX, b.fX), Sk32_sat_sub(a.fY, b.fY) };
 *     }
 *
 *     /** Returns SkIPoint resulting from SkIPoint a offset by ivector b, computed as:
 *         (a.fX + b.fX, a.fY + b.fY).
 *
 *         Can also be used to offset SkIPoint b by ivector a, returning SkIPoint.
 *         Can also be used to add ivector to ivector, returning ivector.
 *
 *         @param a  SkIPoint or ivector to add to
 *         @param b  SkIPoint or ivector to add
 *         @return   SkIPoint equal to a offset by b
 *     */
 *     friend SkIPoint operator+(const SkIPoint& a, const SkIVector& b) {
 *         return { Sk32_sat_add(a.fX, b.fX), Sk32_sat_add(a.fY, b.fY) };
 *     }
 * }
 * ```
 */
public open class SkIPoint public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t fX
   * ```
   */
  public var fX: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fY
   * ```
   */
  public var fY: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t x() const { return fX; }
   * ```
   */
  public fun x(): Int {
    TODO("Implement x")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t y() const { return fY; }
   * ```
   */
  public fun y(): Int {
    return fY
  }

  /**
   * C++ original:
   * ```cpp
   * bool isZero() const { return (fX | fY) == 0; }
   * ```
   */
  public fun isZero(): Boolean {
    return (fX or fY) == 0
  }

  /**
   * C++ original:
   * ```cpp
   * void set(int32_t x, int32_t y) {
   *         fX = x;
   *         fY = y;
   *     }
   * ```
   */
  public fun `set`(x: Int, y: Int) {
    this.fX = x
    this.fY = y
  }

  /**
   * C++ original:
   * ```cpp
   * SkIPoint operator-() const {
   *         return {-fX, -fY};
   *     }
   * ```
   */
  public operator fun unaryMinus(): SkIPoint {
    return SkIPoint.Companion.make(-x(), -y())
  }

  /**
   * C++ original:
   * ```cpp
   * void operator+=(const SkIVector& v) {
   *         fX = Sk32_sat_add(fX, v.fX);
   *         fY = Sk32_sat_add(fY, v.fY);
   *     }
   * ```
   */
  public operator fun plusAssign(v: SkIVector) {
    TODO("Implement plusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * void operator-=(const SkIVector& v) {
   *         fX = Sk32_sat_sub(fX, v.fX);
   *         fY = Sk32_sat_sub(fY, v.fY);
   *     }
   * ```
   */
  public operator fun minusAssign(v: SkIVector) {
    TODO("Implement minusAssign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool equals(int32_t x, int32_t y) const {
   *         return fX == x && fY == y;
   *     }
   * ```
   */
  public override fun equals(other: Any?): Boolean {
    return other is SkIPoint && this.x() == other.x() && this.y() == other.y()
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIPoint Make(int32_t x, int32_t y) {
     *         return {x, y};
     *     }
     * ```
     */
    public fun make(x: Int, y: Int): SkIPoint {
      return Companion.make(x, y)
    }
  }
}
