package org.skia.math

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkSize {
 *     SkScalar fWidth;
 *     SkScalar fHeight;
 *
 *     static constexpr SkSize Make(SkScalar w, SkScalar h) { return {w, h}; }
 *
 *     static constexpr SkSize Make(const SkISize& src) {
 *         return {SkIntToScalar(src.width()), SkIntToScalar(src.height())};
 *     }
 *
 *     static constexpr SkSize MakeEmpty() { return {0, 0}; }
 *
 *     void set(SkScalar w, SkScalar h) { *this = SkSize{w, h}; }
 *
 *     /** Returns true iff fWidth == 0 && fHeight == 0
 *      */
 *     bool isZero() const { return 0 == fWidth && 0 == fHeight; }
 *
 *     /** Returns true if either width or height are <= 0 */
 *     bool isEmpty() const { return fWidth <= 0 || fHeight <= 0; }
 *
 *     /** Set the width and height to 0 */
 *     void setEmpty() { *this = SkSize{0, 0}; }
 *
 *     SkScalar width() const { return fWidth; }
 *     SkScalar height() const { return fHeight; }
 *
 *     bool equals(SkScalar w, SkScalar h) const { return fWidth == w && fHeight == h; }
 *
 *     SkISize toRound() const { return {SkScalarRoundToInt(fWidth), SkScalarRoundToInt(fHeight)}; }
 *
 *     SkISize toCeil() const { return {SkScalarCeilToInt(fWidth), SkScalarCeilToInt(fHeight)}; }
 *
 *     SkISize toFloor() const { return {SkScalarFloorToInt(fWidth), SkScalarFloorToInt(fHeight)}; }
 * }
 * ```
 */
public data class SkSize public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWidth
   * ```
   */
  public var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fHeight
   * ```
   */
  public var fHeight: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(SkScalar w, SkScalar h) { *this = SkSize{w, h}; }
   * ```
   */
  public fun `set`(w: SkScalar, h: SkScalar) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isZero() const { return 0 == fWidth && 0 == fHeight; }
   * ```
   */
  public fun isZero(): Boolean {
    TODO("Implement isZero")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fWidth <= 0 || fHeight <= 0; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEmpty() { *this = SkSize{0, 0}; }
   * ```
   */
  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar width() const { return fWidth; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * SkScalar height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * bool equals(SkScalar w, SkScalar h) const { return fWidth == w && fHeight == h; }
   * ```
   */
  public override fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize toRound() const { return {SkScalarRoundToInt(fWidth), SkScalarRoundToInt(fHeight)}; }
   * ```
   */
  public fun toRound(): SkISize {
    TODO("Implement toRound")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize toCeil() const { return {SkScalarCeilToInt(fWidth), SkScalarCeilToInt(fHeight)}; }
   * ```
   */
  public fun toCeil(): SkISize {
    TODO("Implement toCeil")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize toFloor() const { return {SkScalarFloorToInt(fWidth), SkScalarFloorToInt(fHeight)}; }
   * ```
   */
  public fun toFloor(): SkISize {
    TODO("Implement toFloor")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkSize Make(SkScalar w, SkScalar h) { return {w, h}; }
     * ```
     */
    public fun make(w: SkScalar, h: SkScalar): SkSize {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkSize Make(const SkISize& src) {
     *         return {SkIntToScalar(src.width()), SkIntToScalar(src.height())};
     *     }
     * ```
     */
    public fun make(src: SkISize): SkSize {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkSize MakeEmpty() { return {0, 0}; }
     * ```
     */
    public fun makeEmpty(): SkSize {
      TODO("Implement makeEmpty")
    }
  }
}
