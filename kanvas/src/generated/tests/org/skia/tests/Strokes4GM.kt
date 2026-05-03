package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Strokes4GM : public skiagm::GM {
 * public:
 *     Strokes4GM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("strokes_zoomed"); }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * 2); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(0.055f);
 *
 *         canvas->scale(1000, 1000);
 *         canvas->drawCircle(0, 2, 1.97f, paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class Strokes4GM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("strokes_zoomed"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W, H * 2); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(0.055f);
   *
   *         canvas->scale(1000, 1000);
   *         canvas->drawCircle(0, 2, 1.97f, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
