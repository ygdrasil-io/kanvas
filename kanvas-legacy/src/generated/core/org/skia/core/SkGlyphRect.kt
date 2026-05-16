package org.skia.core

import kotlin.Boolean
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class SkGlyphRect {
 * public:
 *     SkGlyphRect() = default;
 *     SkGlyphRect(SkScalar left, SkScalar top, SkScalar right, SkScalar bottom)
 *             : fRect{-left, -top, right, bottom} { }
 *     bool empty() const {
 *         return -fRect[0] >= fRect[2] || -fRect[1] >= fRect[3];
 *     }
 *     SkRect rect() const {
 *         return SkRect::MakeLTRB(-fRect[0], -fRect[1], fRect[2], fRect[3]);
 *     }
 *     SkGlyphRect offset(SkScalar x, SkScalar y) const {
 *         return SkGlyphRect{fRect + Storage{-x, -y, x, y}};
 *     }
 *     SkGlyphRect offset(SkPoint pt) const {
 *         return this->offset(pt.x(), pt.y());
 *     }
 *     SkGlyphRect scaleAndOffset(SkScalar scale, SkPoint offset) const {
 *         auto [x, y] = offset;
 *         return fRect * scale + Storage{-x, -y, x, y};
 *     }
 *     SkGlyphRect inset(SkScalar dx, SkScalar dy) const {
 *         return fRect - Storage{dx, dy, dx, dy};
 *     }
 *     SkPoint leftTop() const { return -this->negLeftTop(); }
 *     SkPoint rightBottom() const { return {fRect[2], fRect[3]}; }
 *     SkPoint widthHeight() const { return this->rightBottom() + negLeftTop(); }
 *     friend SkGlyphRect skglyph::rect_union(SkGlyphRect, SkGlyphRect);
 *     friend SkGlyphRect skglyph::rect_intersection(SkGlyphRect, SkGlyphRect);
 *
 * private:
 *     SkPoint negLeftTop() const { return {fRect[0], fRect[1]}; }
 *     using Storage = skvx::Vec<4, SkScalar>;
 *     SkGlyphRect(Storage rect) : fRect{rect} { }
 *     Storage fRect;
 * }
 * ```
 */
public data class SkGlyphRect public constructor(
  /**
   * C++ original:
   * ```cpp
   * Storage fRect
   * ```
   */
  private var fRect: SkGlyphRectStorage,
) {
  /**
   * C++ original:
   * ```cpp
   * bool empty() const {
   *         return -fRect[0] >= fRect[2] || -fRect[1] >= fRect[3];
   *     }
   * ```
   */
  public fun empty(): Boolean {
    TODO("Implement empty")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect rect() const {
   *         return SkRect::MakeLTRB(-fRect[0], -fRect[1], fRect[2], fRect[3]);
   *     }
   * ```
   */
  public fun rect(): SkRect {
    TODO("Implement rect")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphRect offset(SkScalar x, SkScalar y) const {
   *         return SkGlyphRect{fRect + Storage{-x, -y, x, y}};
   *     }
   * ```
   */
  public fun offset(x: SkScalar, y: SkScalar): SkGlyphRect {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphRect offset(SkPoint pt) const {
   *         return this->offset(pt.x(), pt.y());
   *     }
   * ```
   */
  public fun offset(pt: SkPoint): SkGlyphRect {
    TODO("Implement offset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphRect scaleAndOffset(SkScalar scale, SkPoint offset) const {
   *         auto [x, y] = offset;
   *         return fRect * scale + Storage{-x, -y, x, y};
   *     }
   * ```
   */
  public fun scaleAndOffset(scale: SkScalar, offset: SkPoint): SkGlyphRect {
    TODO("Implement scaleAndOffset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkGlyphRect inset(SkScalar dx, SkScalar dy) const {
   *         return fRect - Storage{dx, dy, dx, dy};
   *     }
   * ```
   */
  public fun inset(dx: SkScalar, dy: SkScalar): SkGlyphRect {
    TODO("Implement inset")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint leftTop() const { return -this->negLeftTop(); }
   * ```
   */
  public fun leftTop(): SkPoint {
    TODO("Implement leftTop")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint rightBottom() const { return {fRect[2], fRect[3]}; }
   * ```
   */
  public fun rightBottom(): SkPoint {
    TODO("Implement rightBottom")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint widthHeight() const { return this->rightBottom() + negLeftTop(); }
   * ```
   */
  public fun widthHeight(): SkPoint {
    TODO("Implement widthHeight")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint negLeftTop() const { return {fRect[0], fRect[1]}; }
   * ```
   */
  private fun negLeftTop(): SkPoint {
    TODO("Implement negLeftTop")
  }
}
