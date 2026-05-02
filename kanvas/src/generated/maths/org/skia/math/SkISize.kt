package org.skia.math

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.Long

/**
 * C++ original:
 * ```cpp
 * struct SkISize {
 *     int32_t fWidth;
 *     int32_t fHeight;
 *
 *     static constexpr SkISize Make(int32_t w, int32_t h) { return {w, h}; }
 *
 *     static constexpr SkISize MakeEmpty() { return {0, 0}; }
 *
 *     void set(int32_t w, int32_t h) { *this = SkISize{w, h}; }
 *
 *     /** Returns true iff fWidth == 0 && fHeight == 0
 *      */
 *     bool isZero() const { return 0 == fWidth && 0 == fHeight; }
 *
 *     /** Returns true if either width or height are <= 0 */
 *     bool isEmpty() const { return fWidth <= 0 || fHeight <= 0; }
 *
 *     /** Set the width and height to 0 */
 *     void setEmpty() { fWidth = fHeight = 0; }
 *
 *     constexpr int32_t width() const { return fWidth; }
 *     constexpr int32_t height() const { return fHeight; }
 *
 *     constexpr int64_t area() const { return SkToS64(fWidth) * SkToS64(fHeight); }
 *
 *     bool equals(int32_t w, int32_t h) const { return fWidth == w && fHeight == h; }
 * }
 * ```
 */
public data class SkISize public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t fWidth
   * ```
   */
  public var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t fHeight
   * ```
   */
  public var fHeight: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void set(int32_t w, int32_t h) { *this = SkISize{w, h}; }
   * ```
   */
  public fun `set`(w: Int, h: Int) {
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
   * void setEmpty() { fWidth = fHeight = 0; }
   * ```
   */
  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t width() const { return fWidth; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int32_t height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr int64_t area() const { return SkToS64(fWidth) * SkToS64(fHeight); }
   * ```
   */
  public fun area(): Long {
    TODO("Implement area")
  }

  /**
   * C++ original:
   * ```cpp
   * bool equals(int32_t w, int32_t h) const { return fWidth == w && fHeight == h; }
   * ```
   */
  public override fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static constexpr SkISize Make(int32_t w, int32_t h) { return {w, h}; }
     * ```
     */
    public fun make(w: Int, h: Int): SkISize {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static constexpr SkISize MakeEmpty() { return {0, 0}; }
     * ```
     */
    public fun makeEmpty(): SkISize {
      TODO("Implement makeEmpty")
    }
  }
}
