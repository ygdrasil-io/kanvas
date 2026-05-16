package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkColor
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class TeenyStrokesGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("teenyStrokes"); }
 *
 *     SkISize getISize() override { return SkISize::Make(W, H * 2); }
 *
 *     static void line(SkScalar scale, SkCanvas* canvas, SkColor color) {
 *         SkPaint p;
 *         p.setAntiAlias(true);
 *         p.setStyle(SkPaint::kStroke_Style);
 *         p.setColor(color);
 *         canvas->translate(50, 0);
 *         canvas->save();
 *         p.setStrokeWidth(scale * 5);
 *         canvas->scale(1 / scale, 1 / scale);
 *         canvas->drawLine(20 * scale, 20 * scale, 20 * scale, 100 * scale, p);
 *         canvas->drawLine(20 * scale, 20 * scale, 100 * scale, 100 * scale, p);
 *         canvas->restore();
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         line(0.00005f, canvas, SK_ColorBLACK);
 *         line(0.000045f, canvas, SK_ColorRED);
 *         line(0.0000035f, canvas, SK_ColorGREEN);
 *         line(0.000003f, canvas, SK_ColorBLUE);
 *         line(0.000002f, canvas, SK_ColorBLACK);
 *     }
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TeenyStrokesGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("teenyStrokes"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(W, H * 2); }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         line(0.00005f, canvas, SK_ColorBLACK);
   *         line(0.000045f, canvas, SK_ColorRED);
   *         line(0.0000035f, canvas, SK_ColorGREEN);
   *         line(0.000003f, canvas, SK_ColorBLUE);
   *         line(0.000002f, canvas, SK_ColorBLACK);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void line(SkScalar scale, SkCanvas* canvas, SkColor color) {
     *         SkPaint p;
     *         p.setAntiAlias(true);
     *         p.setStyle(SkPaint::kStroke_Style);
     *         p.setColor(color);
     *         canvas->translate(50, 0);
     *         canvas->save();
     *         p.setStrokeWidth(scale * 5);
     *         canvas->scale(1 / scale, 1 / scale);
     *         canvas->drawLine(20 * scale, 20 * scale, 20 * scale, 100 * scale, p);
     *         canvas->drawLine(20 * scale, 20 * scale, 100 * scale, 100 * scale, p);
     *         canvas->restore();
     *     }
     * ```
     */
    private fun line(
      scale: SkScalar,
      canvas: SkCanvas?,
      color: SkColor,
    ) {
      TODO("Implement line")
    }
  }
}
