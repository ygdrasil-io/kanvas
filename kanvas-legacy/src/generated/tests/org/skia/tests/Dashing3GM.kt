package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class Dashing3GM : public skiagm::GM {
 *     SkString getName() const override { return SkString("dashing3"); }
 *
 *     SkISize getISize() override { return {640, 480}; }
 *
 *     // Draw a 100x100 block of dashed lines. The horizontal ones are BW
 *     // while the vertical ones are AA.
 *     void drawDashedLines(SkCanvas* canvas,
 *                          SkScalar lineLength,
 *                          SkScalar phase,
 *                          SkScalar dashLength,
 *                          int strokeWidth,
 *                          bool circles) {
 *         SkPaint p;
 *         p.setColor(SK_ColorBLACK);
 *         p.setStroke(true);
 *         p.setStrokeWidth(SkIntToScalar(strokeWidth));
 *
 *         if (circles) {
 *             p.setStrokeCap(SkPaint::kRound_Cap);
 *         }
 *
 *         SkScalar intervals[2] = { dashLength, dashLength };
 *
 *         p.setPathEffect(SkDashPathEffect::Make(intervals, phase));
 *
 *         SkPoint pts[2];
 *
 *         for (int y = 0; y < 100; y += 10*strokeWidth) {
 *             pts[0].set(0, SkIntToScalar(y));
 *             pts[1].set(lineLength, SkIntToScalar(y));
 *
 *             canvas->drawPoints(SkCanvas::kLines_PointMode, pts, p);
 *         }
 *
 *         p.setAntiAlias(true);
 *
 *         for (int x = 0; x < 100; x += 14*strokeWidth) {
 *             pts[0].set(SkIntToScalar(x), 0);
 *             pts[1].set(SkIntToScalar(x), lineLength);
 *
 *             canvas->drawPoints(SkCanvas::kLines_PointMode, pts, p);
 *         }
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // 1on/1off 1x1 squares with phase of 0 - points fastpath
 *         canvas->save();
 *             canvas->translate(2, 0);
 *             this->drawDashedLines(canvas, 100, 0, SK_Scalar1, 1, false);
 *         canvas->restore();
 *
 *         // 1on/1off 1x1 squares with phase of .5 - rects fastpath (due to partial squares)
 *         canvas->save();
 *             canvas->translate(112, 0);
 *             this->drawDashedLines(canvas, 100, SK_ScalarHalf, SK_Scalar1, 1, false);
 *         canvas->restore();
 *
 *         // 1on/1off 1x1 squares with phase of 1 - points fastpath
 *         canvas->save();
 *             canvas->translate(222, 0);
 *             this->drawDashedLines(canvas, 100, SK_Scalar1, SK_Scalar1, 1, false);
 *         canvas->restore();
 *
 *         // 1on/1off 1x1 squares with phase of 1 and non-integer length - rects fastpath
 *         canvas->save();
 *             canvas->translate(332, 0);
 *             this->drawDashedLines(canvas, 99.5f, SK_ScalarHalf, SK_Scalar1, 1, false);
 *         canvas->restore();
 *
 *         // 255on/255off 1x1 squares with phase of 0 - rects fast path
 *         canvas->save();
 *             canvas->translate(446, 0);
 *             this->drawDashedLines(canvas, 100, 0, SkIntToScalar(255), 1, false);
 *         canvas->restore();
 *
 *         // 1on/1off 3x3 squares with phase of 0 - points fast path
 *         canvas->save();
 *             canvas->translate(2, 110);
 *             this->drawDashedLines(canvas, 100, 0, SkIntToScalar(3), 3, false);
 *         canvas->restore();
 *
 *         // 1on/1off 3x3 squares with phase of 1.5 - rects fast path
 *         canvas->save();
 *             canvas->translate(112, 110);
 *             this->drawDashedLines(canvas, 100, 1.5f, SkIntToScalar(3), 3, false);
 *         canvas->restore();
 *
 *         // 1on/1off 1x1 circles with phase of 1 - no fast path yet
 *         canvas->save();
 *             canvas->translate(2, 220);
 *             this->drawDashedLines(canvas, 100, SK_Scalar1, SK_Scalar1, 1, true);
 *         canvas->restore();
 *
 *         // 1on/1off 3x3 circles with phase of 1 - no fast path yet
 *         canvas->save();
 *             canvas->translate(112, 220);
 *             this->drawDashedLines(canvas, 100, 0, SkIntToScalar(3), 3, true);
 *         canvas->restore();
 *
 *         // 1on/1off 1x1 squares with rotation - should break fast path
 *         canvas->save();
 *             canvas->translate(332+SK_ScalarRoot2Over2*100, 110+SK_ScalarRoot2Over2*100);
 *             canvas->rotate(45);
 *             canvas->translate(-50, -50);
 *
 *             this->drawDashedLines(canvas, 100, SK_Scalar1, SK_Scalar1, 1, false);
 *         canvas->restore();
 *
 *         // 3on/3off 3x1 rects - should use rect fast path regardless of phase
 *         for (int phase = 0; phase <= 3; ++phase) {
 *             canvas->save();
 *                 canvas->translate(SkIntToScalar(phase*110+2),
 *                                   SkIntToScalar(330));
 *                 this->drawDashedLines(canvas, 100, SkIntToScalar(phase), SkIntToScalar(3), 1, false);
 *             canvas->restore();
 *         }
 *     }
 *
 * }
 * ```
 */
public open class Dashing3GM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("dashing3"); }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {640, 480}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawDashedLines(SkCanvas* canvas,
   *                          SkScalar lineLength,
   *                          SkScalar phase,
   *                          SkScalar dashLength,
   *                          int strokeWidth,
   *                          bool circles) {
   *         SkPaint p;
   *         p.setColor(SK_ColorBLACK);
   *         p.setStroke(true);
   *         p.setStrokeWidth(SkIntToScalar(strokeWidth));
   *
   *         if (circles) {
   *             p.setStrokeCap(SkPaint::kRound_Cap);
   *         }
   *
   *         SkScalar intervals[2] = { dashLength, dashLength };
   *
   *         p.setPathEffect(SkDashPathEffect::Make(intervals, phase));
   *
   *         SkPoint pts[2];
   *
   *         for (int y = 0; y < 100; y += 10*strokeWidth) {
   *             pts[0].set(0, SkIntToScalar(y));
   *             pts[1].set(lineLength, SkIntToScalar(y));
   *
   *             canvas->drawPoints(SkCanvas::kLines_PointMode, pts, p);
   *         }
   *
   *         p.setAntiAlias(true);
   *
   *         for (int x = 0; x < 100; x += 14*strokeWidth) {
   *             pts[0].set(SkIntToScalar(x), 0);
   *             pts[1].set(SkIntToScalar(x), lineLength);
   *
   *             canvas->drawPoints(SkCanvas::kLines_PointMode, pts, p);
   *         }
   *     }
   * ```
   */
  private fun drawDashedLines(
    canvas: SkCanvas?,
    lineLength: SkScalar,
    phase: SkScalar,
    dashLength: SkScalar,
    strokeWidth: Int,
    circles: Boolean,
  ) {
    TODO("Implement drawDashedLines")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // 1on/1off 1x1 squares with phase of 0 - points fastpath
   *         canvas->save();
   *             canvas->translate(2, 0);
   *             this->drawDashedLines(canvas, 100, 0, SK_Scalar1, 1, false);
   *         canvas->restore();
   *
   *         // 1on/1off 1x1 squares with phase of .5 - rects fastpath (due to partial squares)
   *         canvas->save();
   *             canvas->translate(112, 0);
   *             this->drawDashedLines(canvas, 100, SK_ScalarHalf, SK_Scalar1, 1, false);
   *         canvas->restore();
   *
   *         // 1on/1off 1x1 squares with phase of 1 - points fastpath
   *         canvas->save();
   *             canvas->translate(222, 0);
   *             this->drawDashedLines(canvas, 100, SK_Scalar1, SK_Scalar1, 1, false);
   *         canvas->restore();
   *
   *         // 1on/1off 1x1 squares with phase of 1 and non-integer length - rects fastpath
   *         canvas->save();
   *             canvas->translate(332, 0);
   *             this->drawDashedLines(canvas, 99.5f, SK_ScalarHalf, SK_Scalar1, 1, false);
   *         canvas->restore();
   *
   *         // 255on/255off 1x1 squares with phase of 0 - rects fast path
   *         canvas->save();
   *             canvas->translate(446, 0);
   *             this->drawDashedLines(canvas, 100, 0, SkIntToScalar(255), 1, false);
   *         canvas->restore();
   *
   *         // 1on/1off 3x3 squares with phase of 0 - points fast path
   *         canvas->save();
   *             canvas->translate(2, 110);
   *             this->drawDashedLines(canvas, 100, 0, SkIntToScalar(3), 3, false);
   *         canvas->restore();
   *
   *         // 1on/1off 3x3 squares with phase of 1.5 - rects fast path
   *         canvas->save();
   *             canvas->translate(112, 110);
   *             this->drawDashedLines(canvas, 100, 1.5f, SkIntToScalar(3), 3, false);
   *         canvas->restore();
   *
   *         // 1on/1off 1x1 circles with phase of 1 - no fast path yet
   *         canvas->save();
   *             canvas->translate(2, 220);
   *             this->drawDashedLines(canvas, 100, SK_Scalar1, SK_Scalar1, 1, true);
   *         canvas->restore();
   *
   *         // 1on/1off 3x3 circles with phase of 1 - no fast path yet
   *         canvas->save();
   *             canvas->translate(112, 220);
   *             this->drawDashedLines(canvas, 100, 0, SkIntToScalar(3), 3, true);
   *         canvas->restore();
   *
   *         // 1on/1off 1x1 squares with rotation - should break fast path
   *         canvas->save();
   *             canvas->translate(332+SK_ScalarRoot2Over2*100, 110+SK_ScalarRoot2Over2*100);
   *             canvas->rotate(45);
   *             canvas->translate(-50, -50);
   *
   *             this->drawDashedLines(canvas, 100, SK_Scalar1, SK_Scalar1, 1, false);
   *         canvas->restore();
   *
   *         // 3on/3off 3x1 rects - should use rect fast path regardless of phase
   *         for (int phase = 0; phase <= 3; ++phase) {
   *             canvas->save();
   *                 canvas->translate(SkIntToScalar(phase*110+2),
   *                                   SkIntToScalar(330));
   *                 this->drawDashedLines(canvas, 100, SkIntToScalar(phase), SkIntToScalar(3), 1, false);
   *             canvas->restore();
   *         }
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
