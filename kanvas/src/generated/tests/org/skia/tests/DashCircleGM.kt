package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class DashCircleGM : public skiagm::GM {
 * public:
 *     DashCircleGM() : fRotation(0) { }
 *
 * protected:
 *     SkString getName() const override { return SkString("dashcircle"); }
 *
 *     SkISize getISize() override { return SkISize::Make(900, 1200); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint refPaint;
 *         refPaint.setAntiAlias(true);
 *         refPaint.setColor(0xFFbf3f7f);
 *         refPaint.setStroke(true);
 *         refPaint.setStrokeWidth(1);
 *         const SkScalar radius = 125;
 *         SkRect oval = SkRect::MakeLTRB(-radius - 20, -radius - 20, radius + 20, radius + 20);
 *         SkPath circle = SkPath::Circle(0, 0, radius);
 *         SkScalar circumference = radius * SK_ScalarPI * 2;
 *         int wedges[] = { 6, 12, 36 };
 *         canvas->translate(radius+20, radius+20);
 *         for (int wedge : wedges) {
 *             SkScalar arcLength = 360.f / wedge;
 *             canvas->save();
 *             for (const DashExample& dashExample : dashExamples) {
 *                 SkPathBuilder refPath;
 *                 int dashUnits = 0;
 *                 for (int index = 0; index < dashExample.length; ++index) {
 *                     dashUnits += dashExample.pattern[index];
 *                 }
 *                 SkScalar unitLength = arcLength / dashUnits;
 *                 SkScalar angle = 0;
 *                 for (int index = 0; index < wedge; ++index) {
 *                     for (int i2 = 0; i2 < dashExample.length; i2 += 2) {
 *                         SkScalar span = dashExample.pattern[i2] * unitLength;
 *                         refPath.moveTo(0, 0);
 *                         refPath.arcTo(oval, angle, span, false);
 *                         refPath.close();
 *                         angle += span + (dashExample.pattern[i2 + 1]) * unitLength;
 *                     }
 *                 }
 *                 canvas->save();
 *                 canvas->rotate(fRotation);
 *                 canvas->drawPath(refPath.detach(), refPaint);
 *                 canvas->restore();
 *                 SkPaint p;
 *                 p.setAntiAlias(true);
 *                 p.setStroke(true);
 *                 p.setStrokeWidth(10);
 *                 SkScalar intervals[4];
 *                 size_t intervalCount = dashExample.length;
 *                 SkScalar dashLength = circumference / wedge / dashUnits;
 *                 for (int index = 0; index < dashExample.length; ++index) {
 *                     intervals[index] = dashExample.pattern[index] * dashLength;
 *                 }
 *                 p.setPathEffect(SkDashPathEffect::Make({intervals, intervalCount}, 0));
 *                 canvas->save();
 *                 canvas->rotate(fRotation);
 *                 canvas->drawPath(circle, p);
 *                 canvas->restore();
 *                 canvas->translate(0, radius * 2 + 50);
 *             }
 *             canvas->restore();
 *             canvas->translate(radius * 2 + 50, 0);
 *         }
 *     }
 *
 *     bool onAnimate(double nanos) override {
 *         constexpr SkScalar kDesiredDurationSecs = 100.0f;
 *
 *         fRotation = TimeUtils::Scaled(1e-9 * nanos, 360.0f/kDesiredDurationSecs, 360.0f);
 *         return true;
 *     }
 *
 * private:
 *     SkScalar fRotation;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class DashCircleGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRotation
   * ```
   */
  private var fRotation: SkScalar = TODO("Initialize fRotation")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("dashcircle"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(900, 1200); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint refPaint;
   *         refPaint.setAntiAlias(true);
   *         refPaint.setColor(0xFFbf3f7f);
   *         refPaint.setStroke(true);
   *         refPaint.setStrokeWidth(1);
   *         const SkScalar radius = 125;
   *         SkRect oval = SkRect::MakeLTRB(-radius - 20, -radius - 20, radius + 20, radius + 20);
   *         SkPath circle = SkPath::Circle(0, 0, radius);
   *         SkScalar circumference = radius * SK_ScalarPI * 2;
   *         int wedges[] = { 6, 12, 36 };
   *         canvas->translate(radius+20, radius+20);
   *         for (int wedge : wedges) {
   *             SkScalar arcLength = 360.f / wedge;
   *             canvas->save();
   *             for (const DashExample& dashExample : dashExamples) {
   *                 SkPathBuilder refPath;
   *                 int dashUnits = 0;
   *                 for (int index = 0; index < dashExample.length; ++index) {
   *                     dashUnits += dashExample.pattern[index];
   *                 }
   *                 SkScalar unitLength = arcLength / dashUnits;
   *                 SkScalar angle = 0;
   *                 for (int index = 0; index < wedge; ++index) {
   *                     for (int i2 = 0; i2 < dashExample.length; i2 += 2) {
   *                         SkScalar span = dashExample.pattern[i2] * unitLength;
   *                         refPath.moveTo(0, 0);
   *                         refPath.arcTo(oval, angle, span, false);
   *                         refPath.close();
   *                         angle += span + (dashExample.pattern[i2 + 1]) * unitLength;
   *                     }
   *                 }
   *                 canvas->save();
   *                 canvas->rotate(fRotation);
   *                 canvas->drawPath(refPath.detach(), refPaint);
   *                 canvas->restore();
   *                 SkPaint p;
   *                 p.setAntiAlias(true);
   *                 p.setStroke(true);
   *                 p.setStrokeWidth(10);
   *                 SkScalar intervals[4];
   *                 size_t intervalCount = dashExample.length;
   *                 SkScalar dashLength = circumference / wedge / dashUnits;
   *                 for (int index = 0; index < dashExample.length; ++index) {
   *                     intervals[index] = dashExample.pattern[index] * dashLength;
   *                 }
   *                 p.setPathEffect(SkDashPathEffect::Make({intervals, intervalCount}, 0));
   *                 canvas->save();
   *                 canvas->rotate(fRotation);
   *                 canvas->drawPath(circle, p);
   *                 canvas->restore();
   *                 canvas->translate(0, radius * 2 + 50);
   *             }
   *             canvas->restore();
   *             canvas->translate(radius * 2 + 50, 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         constexpr SkScalar kDesiredDurationSecs = 100.0f;
   *
   *         fRotation = TimeUtils::Scaled(1e-9 * nanos, 360.0f/kDesiredDurationSecs, 360.0f);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }
}
