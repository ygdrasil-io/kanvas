package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class Dashing4GM : public skiagm::GM {
 *     SkString getName() const override { return SkString("dashing4"); }
 *
 *     SkISize getISize() override { return {640, 1100}; }
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
 *         canvas->translate(SK_ScalarHalf, SK_ScalarHalf);
 *
 *         for (int width = 0; width <= 2; ++width) {
 *             for (const Intervals& data : {Intervals{1, 1},
 *                                           Intervals{4, 2},
 *                                           Intervals{0, 4}}) { // test for zero length on interval.
 *                                                               // zero length intervals should draw
 *                                                               // a line of squares or circles
 *                 for (bool aa : {false, true}) {
 *                     for (auto cap : {SkPaint::kRound_Cap, SkPaint::kSquare_Cap}) {
 *                         int w = width * width * width;
 *                         paint.setAntiAlias(aa);
 *                         paint.setStrokeWidth(SkIntToScalar(w));
 *                         paint.setStrokeCap(cap);
 *
 *                         int scale = w ? w : 1;
 *
 *                         drawline(canvas, data.fOnInterval * scale, data.fOffInterval * scale,
 *                                  paint);
 *                         canvas->translate(0, SkIntToScalar(20));
 *                     }
 *                 }
 *             }
 *         }
 *
 *         for (int aa = 0; aa <= 1; ++aa) {
 *             paint.setAntiAlias(SkToBool(aa));
 *             paint.setStrokeWidth(8.f);
 *             paint.setStrokeCap(SkPaint::kSquare_Cap);
 *             // Single dash element that is cut off at start and end
 *             drawline(canvas, 32, 16, paint, 20.f, 0, 5.f);
 *             canvas->translate(0, SkIntToScalar(20));
 *
 *             // Two dash elements where each one is cut off at beginning and end respectively
 *             drawline(canvas, 32, 16, paint, 56.f, 0, 5.f);
 *             canvas->translate(0, SkIntToScalar(20));
 *
 *             // Many dash elements where first and last are cut off at beginning and end respectively
 *             drawline(canvas, 32, 16, paint, 584.f, 0, 5.f);
 *             canvas->translate(0, SkIntToScalar(20));
 *
 *             // Diagonal dash line where src pnts are not axis aligned (as apposed to being diagonal from
 *             // a canvas rotation)
 *             drawline(canvas, 32, 16, paint, 600.f, 30.f);
 *             canvas->translate(0, SkIntToScalar(20));
 *
 *             // Case where only the off interval exists on the line. Thus nothing should be drawn
 *             drawline(canvas, 32, 16, paint, 8.f, 0.f, 40.f);
 *             canvas->translate(0, SkIntToScalar(20));
 *         }
 *
 *         // Test overlapping circles.
 *         canvas->translate(SkIntToScalar(5), SkIntToScalar(20));
 *         paint.setAntiAlias(true);
 *         paint.setStrokeCap(SkPaint::kRound_Cap);
 *         paint.setColor(0x44000000);
 *         paint.setStrokeWidth(40);
 *         drawline(canvas, 0, 30, paint);
 *
 *         canvas->translate(0, SkIntToScalar(50));
 *         paint.setStrokeCap(SkPaint::kSquare_Cap);
 *         drawline(canvas, 0, 30, paint);
 *
 *         // Test we draw the cap when the line length is zero.
 *         canvas->translate(0, SkIntToScalar(50));
 *         paint.setStrokeCap(SkPaint::kRound_Cap);
 *         paint.setColor(0xFF000000);
 *         paint.setStrokeWidth(11);
 *         drawline(canvas, 0, 30, paint, 0);
 *
 *         canvas->translate(SkIntToScalar(100), 0);
 *         drawline(canvas, 1, 30, paint, 0);
 *     }
 * }
 * ```
 */
public open class Dashing4GM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("dashing4"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 1100}; }
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
   *         canvas->translate(SK_ScalarHalf, SK_ScalarHalf);
   *
   *         for (int width = 0; width <= 2; ++width) {
   *             for (const Intervals& data : {Intervals{1, 1},
   *                                           Intervals{4, 2},
   *                                           Intervals{0, 4}}) { // test for zero length on interval.
   *                                                               // zero length intervals should draw
   *                                                               // a line of squares or circles
   *                 for (bool aa : {false, true}) {
   *                     for (auto cap : {SkPaint::kRound_Cap, SkPaint::kSquare_Cap}) {
   *                         int w = width * width * width;
   *                         paint.setAntiAlias(aa);
   *                         paint.setStrokeWidth(SkIntToScalar(w));
   *                         paint.setStrokeCap(cap);
   *
   *                         int scale = w ? w : 1;
   *
   *                         drawline(canvas, data.fOnInterval * scale, data.fOffInterval * scale,
   *                                  paint);
   *                         canvas->translate(0, SkIntToScalar(20));
   *                     }
   *                 }
   *             }
   *         }
   *
   *         for (int aa = 0; aa <= 1; ++aa) {
   *             paint.setAntiAlias(SkToBool(aa));
   *             paint.setStrokeWidth(8.f);
   *             paint.setStrokeCap(SkPaint::kSquare_Cap);
   *             // Single dash element that is cut off at start and end
   *             drawline(canvas, 32, 16, paint, 20.f, 0, 5.f);
   *             canvas->translate(0, SkIntToScalar(20));
   *
   *             // Two dash elements where each one is cut off at beginning and end respectively
   *             drawline(canvas, 32, 16, paint, 56.f, 0, 5.f);
   *             canvas->translate(0, SkIntToScalar(20));
   *
   *             // Many dash elements where first and last are cut off at beginning and end respectively
   *             drawline(canvas, 32, 16, paint, 584.f, 0, 5.f);
   *             canvas->translate(0, SkIntToScalar(20));
   *
   *             // Diagonal dash line where src pnts are not axis aligned (as apposed to being diagonal from
   *             // a canvas rotation)
   *             drawline(canvas, 32, 16, paint, 600.f, 30.f);
   *             canvas->translate(0, SkIntToScalar(20));
   *
   *             // Case where only the off interval exists on the line. Thus nothing should be drawn
   *             drawline(canvas, 32, 16, paint, 8.f, 0.f, 40.f);
   *             canvas->translate(0, SkIntToScalar(20));
   *         }
   *
   *         // Test overlapping circles.
   *         canvas->translate(SkIntToScalar(5), SkIntToScalar(20));
   *         paint.setAntiAlias(true);
   *         paint.setStrokeCap(SkPaint::kRound_Cap);
   *         paint.setColor(0x44000000);
   *         paint.setStrokeWidth(40);
   *         drawline(canvas, 0, 30, paint);
   *
   *         canvas->translate(0, SkIntToScalar(50));
   *         paint.setStrokeCap(SkPaint::kSquare_Cap);
   *         drawline(canvas, 0, 30, paint);
   *
   *         // Test we draw the cap when the line length is zero.
   *         canvas->translate(0, SkIntToScalar(50));
   *         paint.setStrokeCap(SkPaint::kRound_Cap);
   *         paint.setColor(0xFF000000);
   *         paint.setStrokeWidth(11);
   *         drawline(canvas, 0, 30, paint, 0);
   *
   *         canvas->translate(SkIntToScalar(100), 0);
   *         drawline(canvas, 1, 30, paint, 0);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
