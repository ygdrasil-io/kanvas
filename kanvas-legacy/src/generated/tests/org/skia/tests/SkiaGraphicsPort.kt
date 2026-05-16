package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkiaGraphicsPort : public GraphicsPort {
 * public:
 *     SkiaGraphicsPort(SkCanvas* canvas) : GraphicsPort(canvas) {}
 *
 *     void drawRect(const SkRect& r, SkColor c) override {
 *         SkCanvas* canvas = (SkCanvas*)fCanvas->accessTopRasterHandle();
 *         canvas->drawRect(r, SkPaint(SkColor4f::FromColor(c)));
 *     }
 * }
 * ```
 */
public open class SkiaGraphicsPort public constructor(
  canvas: SkCanvas?,
) : GraphicsPort(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void drawRect(const SkRect& r, SkColor c) override {
   *         SkCanvas* canvas = (SkCanvas*)fCanvas->accessTopRasterHandle();
   *         canvas->drawRect(r, SkPaint(SkColor4f::FromColor(c)));
   *     }
   * ```
   */
  public override fun drawRect(r: SkRect, c: SkColor) {
    TODO("Implement drawRect")
  }
}
