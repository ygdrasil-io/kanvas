package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkIPoint16 {
 *     int16_t fX; //!< x-axis value used by SkIPoint16
 *
 *     int16_t fY; //!< y-axis value used by SkIPoint16
 *
 *     /** Sets fX to x, fY to y. If SK_DEBUG is defined, asserts
 *      if x or y does not fit in 16 bits.
 *
 *      @param x  integer x-axis value of constructed SkIPoint
 *      @param y  integer y-axis value of constructed SkIPoint
 *      @return   SkIPoint16 (x, y)
 *      */
 *     static constexpr SkIPoint16 Make(int x, int y) {
 *         return {SkToS16(x), SkToS16(y)};
 *     }
 *
 *     /** Returns x-axis value of SkIPoint16.
 *
 *      @return  fX
 *      */
 *     int16_t x() const { return fX; }
 *
 *     /** Returns y-axis value of SkIPoint.
 *
 *      @return  fY
 *      */
 *     int16_t y() const { return fY; }
 *
 *     /** Sets fX to x and fY to y.
 *
 *      @param x  new value for fX
 *      @param y  new value for fY
 *      */
 *     void set(int x, int y) {
 *         fX = SkToS16(x);
 *         fY = SkToS16(y);
 *     }
 * }
 * ```
 */
public data class SkIPoint16 public constructor(
  /**
   * C++ original:
   * ```cpp
   * int16_t fX
   * ```
   */
  public var fX: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fY
   * ```
   */
  public var fY: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int16_t x() const { return fX; }
   * ```
   */
  public fun x(): Int {
    TODO("Implement x")
  }

  /**
   * C++ original:
   * ```cpp
   * int16_t y() const { return fY; }
   * ```
   */
  public fun y(): Int {
    TODO("Implement y")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(int x, int y) {
   *         fX = SkToS16(x);
   *         fY = SkToS16(y);
   *     }
   * ```
   */
  public fun `set`(x: Int, y: Int) {
    TODO("Implement set")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkIPoint16 Make(int x, int y) {
     *         return {SkToS16(x), SkToS16(y)};
     *     }
     * ```
     */
    public fun make(x: Int, y: Int): SkIPoint16 {
      TODO("Implement make")
    }
  }
}
