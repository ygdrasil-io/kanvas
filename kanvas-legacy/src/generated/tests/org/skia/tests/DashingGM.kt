package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DashingGM : public skiagm::GM {
 *     SkString getName() const override { return SkString("dashing"); }
 *
 *     SkISize getISize() override { return {640, 340}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         struct Intervals {
 *             int fOnInterval;
 *             int fOffInterval;
 *         };
 *
 *         SkPaint paint;
 *         paint.setStroke(true);
 *
 *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
 *         canvas->translate(0, SK_ScalarHalf);
 *         for (int width = 0; width <= 2; ++width) {
 *             for (const Intervals& data : {Intervals{1, 1},
 *                                           Intervals{4, 1}}) {
 *                 for (bool aa : {false, true}) {
 *                     int w = width * width * width;
 *                     paint.setAntiAlias(aa);
 *                     paint.setStrokeWidth(SkIntToScalar(w));
 *
 *                     int scale = w ? w : 1;
 *
 *                     drawline(canvas, data.fOnInterval * scale, data.fOffInterval * scale,
 *                              paint);
 *                     canvas->translate(0, SkIntToScalar(20));
 *                 }
 *             }
 *         }
 *
 *         show_giant_dash(canvas);
 *         canvas->translate(0, SkIntToScalar(20));
 *         show_zero_len_dash(canvas);
 *         canvas->translate(0, SkIntToScalar(20));
 *         // Draw 0 on, 0 off dashed line
 *         paint.setStrokeWidth(SkIntToScalar(8));
 *         drawline(canvas, 0, 0, paint);
 *     }
 * }
 * ```
 */
public open class DashingGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("dashing"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 340}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         struct Intervals {
   *             int fOnInterval;
   *             int fOffInterval;
   *         };
   *
   *         SkPaint paint;
   *         paint.setStroke(true);
   *
   *         canvas->translate(SkIntToScalar(20), SkIntToScalar(20));
   *         canvas->translate(0, SK_ScalarHalf);
   *         for (int width = 0; width <= 2; ++width) {
   *             for (const Intervals& data : {Intervals{1, 1},
   *                                           Intervals{4, 1}}) {
   *                 for (bool aa : {false, true}) {
   *                     int w = width * width * width;
   *                     paint.setAntiAlias(aa);
   *                     paint.setStrokeWidth(SkIntToScalar(w));
   *
   *                     int scale = w ? w : 1;
   *
   *                     drawline(canvas, data.fOnInterval * scale, data.fOffInterval * scale,
   *                              paint);
   *                     canvas->translate(0, SkIntToScalar(20));
   *                 }
   *             }
   *         }
   *
   *         show_giant_dash(canvas);
   *         canvas->translate(0, SkIntToScalar(20));
   *         show_zero_len_dash(canvas);
   *         canvas->translate(0, SkIntToScalar(20));
   *         // Draw 0 on, 0 off dashed line
   *         paint.setStrokeWidth(SkIntToScalar(8));
   *         drawline(canvas, 0, 0, paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
