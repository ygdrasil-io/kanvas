package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import kotlin.Short
import org.skia.math.SkIRect

/**
 * C++ original:
 * ```cpp
 * struct IRect16 {
 *     int16_t fLeft, fTop, fRight, fBottom;
 *
 *     [[nodiscard]] static IRect16 MakeEmpty() {
 *         IRect16 r;
 *         r.setEmpty();
 *         return r;
 *     }
 *
 *     [[nodiscard]] static IRect16 MakeWH(int16_t w, int16_t h) {
 *         IRect16 r;
 *         r.set(0, 0, w, h);
 *         return r;
 *     }
 *
 *     [[nodiscard]] static IRect16 MakeXYWH(int16_t x, int16_t y, int16_t w, int16_t h) {
 *         IRect16 r;
 *         r.set(x, y, x + w, y + h);
 *         return r;
 *     }
 *
 *     [[nodiscard]] static IRect16 Make(const SkIRect& ir) {
 *         IRect16 r;
 *         r.set(ir);
 *         return r;
 *     }
 *
 *     int width() const { return fRight - fLeft; }
 *     int height() const { return fBottom - fTop; }
 *     int area() const { return this->width() * this->height(); }
 *     bool isEmpty() const { return fLeft >= fRight || fTop >= fBottom; }
 *
 *     void setEmpty() { memset(this, 0, sizeof(*this)); }
 *
 *     void set(int16_t left, int16_t top, int16_t right, int16_t bottom) {
 *         fLeft = left;
 *         fTop = top;
 *         fRight = right;
 *         fBottom = bottom;
 *     }
 *
 *     void set(const SkIRect& r) {
 *         fLeft   = SkToS16(r.fLeft);
 *         fTop    = SkToS16(r.fTop);
 *         fRight  = SkToS16(r.fRight);
 *         fBottom = SkToS16(r.fBottom);
 *     }
 *
 *     void offset(int16_t dx, int16_t dy) {
 *         fLeft   += dx;
 *         fTop    += dy;
 *         fRight  += dx;
 *         fBottom += dy;
 *     }
 * }
 * ```
 */
public data class IRect16 public constructor(
  /**
   * C++ original:
   * ```cpp
   * int16_t fLeft
   * ```
   */
  public var fLeft: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fLeft, fTop
   * ```
   */
  public var fTop: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fLeft, fTop, fRight
   * ```
   */
  public var fRight: Int,
  /**
   * C++ original:
   * ```cpp
   * int16_t fLeft, fTop, fRight, fBottom
   * ```
   */
  public var fBottom: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int width() const { return fRight - fLeft; }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int height() const { return fBottom - fTop; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * int area() const { return this->width() * this->height(); }
   * ```
   */
  public fun area(): Int {
    TODO("Implement area")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isEmpty() const { return fLeft >= fRight || fTop >= fBottom; }
   * ```
   */
  public fun isEmpty(): Boolean {
    TODO("Implement isEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void setEmpty() { memset(this, 0, sizeof(*this)); }
   * ```
   */
  public fun setEmpty() {
    TODO("Implement setEmpty")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(int16_t left, int16_t top, int16_t right, int16_t bottom) {
   *         fLeft = left;
   *         fTop = top;
   *         fRight = right;
   *         fBottom = bottom;
   *     }
   * ```
   */
  public fun `set`(
    left: Short,
    top: Short,
    right: Short,
    bottom: Short,
  ) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void set(const SkIRect& r) {
   *         fLeft   = SkToS16(r.fLeft);
   *         fTop    = SkToS16(r.fTop);
   *         fRight  = SkToS16(r.fRight);
   *         fBottom = SkToS16(r.fBottom);
   *     }
   * ```
   */
  public fun `set`(r: SkIRect) {
    TODO("Implement set")
  }

  /**
   * C++ original:
   * ```cpp
   * void offset(int16_t dx, int16_t dy) {
   *         fLeft   += dx;
   *         fTop    += dy;
   *         fRight  += dx;
   *         fBottom += dy;
   *     }
   * ```
   */
  public fun offset(dx: Short, dy: Short) {
    TODO("Implement offset")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static IRect16 MakeEmpty() {
     *         IRect16 r;
     *         r.setEmpty();
     *         return r;
     *     }
     * ```
     */
    public fun makeEmpty(): IRect16 {
      TODO("Implement makeEmpty")
    }

    /**
     * C++ original:
     * ```cpp
     * static IRect16 MakeWH(int16_t w, int16_t h) {
     *         IRect16 r;
     *         r.set(0, 0, w, h);
     *         return r;
     *     }
     * ```
     */
    public fun makeWH(w: Short, h: Short): IRect16 {
      TODO("Implement makeWH")
    }

    /**
     * C++ original:
     * ```cpp
     * static IRect16 MakeXYWH(int16_t x, int16_t y, int16_t w, int16_t h) {
     *         IRect16 r;
     *         r.set(x, y, x + w, y + h);
     *         return r;
     *     }
     * ```
     */
    public fun makeXYWH(
      x: Short,
      y: Short,
      w: Short,
      h: Short,
    ): IRect16 {
      TODO("Implement makeXYWH")
    }

    /**
     * C++ original:
     * ```cpp
     * static IRect16 Make(const SkIRect& ir) {
     *         IRect16 r;
     *         r.set(ir);
     *         return r;
     *     }
     * ```
     */
    public fun make(ir: SkIRect): IRect16 {
      TODO("Implement make")
    }
  }
}
