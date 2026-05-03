package org.skia.tests

import kotlin.Boolean
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.utils.SkPaintFilterCanvas

/**
 * C++ original:
 * ```cpp
 * class MockFilterCanvas : public SkPaintFilterCanvas {
 * public:
 *     MockFilterCanvas(SkCanvas* canvas) : INHERITED(canvas) { }
 *
 * protected:
 *     bool onFilter(SkPaint&) const override { return true; }
 *
 * private:
 *     using INHERITED = SkPaintFilterCanvas;
 * }
 * ```
 */
public open class MockFilterCanvas public constructor(
  canvas: SkCanvas?,
) : SkPaintFilterCanvas(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * bool onFilter(SkPaint&) const override { return true; }
   * ```
   */
  protected override fun onFilter(param0: SkPaint): Boolean {
    TODO("Implement onFilter")
  }
}
