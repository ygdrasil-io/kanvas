package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class TestGradientGM : public skiagm::GM {
 * public:
 *     TestGradientGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("testgradient"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 800); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         // Set up a gradient paint for a rect.
 *         // And non-gradient paint for other objects.
 *         canvas->drawColor(SK_ColorWHITE);
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kFill_Style);
 *         paint.setAntiAlias(true);
 *         paint.setStrokeWidth(4);
 *         paint.setColor(0xFFFE938C);
 *
 *         SkRect rect = SkRect::MakeXYWH(10, 10, 100, 160);
 *
 *         SkPoint points[2] = {
 *             SkPoint::Make(0.0f, 0.0f),
 *             SkPoint::Make(256.0f, 256.0f)
 *         };
 *         const SkColor4f colors[2] = {SkColors::kBlue, SkColors::kYellow};
 *         SkPaint newPaint(paint);
 *         newPaint.setShader(SkShaders::LinearGradient(
 *                 points, {{colors, {}, SkTileMode::kClamp}, {}}));
 *         canvas->drawRect(rect, newPaint);
 *
 *         SkRRect oval;
 *         oval.setOval(rect);
 *         oval.offset(40, 80);
 *         paint.setColor(0xFFE6B89C);
 *         canvas->drawRRect(oval, paint);
 *
 *         paint.setColor(0xFF9CAFB7);
 *         canvas->drawCircle(180, 50, 25, paint);
 *
 *         rect.offset(80, 50);
 *         paint.setColor(0xFF4281A4);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         canvas->drawRoundRect(rect, 10, 10, paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class TestGradientGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("testgradient"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 800); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         // Set up a gradient paint for a rect.
   *         // And non-gradient paint for other objects.
   *         canvas->drawColor(SK_ColorWHITE);
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kFill_Style);
   *         paint.setAntiAlias(true);
   *         paint.setStrokeWidth(4);
   *         paint.setColor(0xFFFE938C);
   *
   *         SkRect rect = SkRect::MakeXYWH(10, 10, 100, 160);
   *
   *         SkPoint points[2] = {
   *             SkPoint::Make(0.0f, 0.0f),
   *             SkPoint::Make(256.0f, 256.0f)
   *         };
   *         const SkColor4f colors[2] = {SkColors::kBlue, SkColors::kYellow};
   *         SkPaint newPaint(paint);
   *         newPaint.setShader(SkShaders::LinearGradient(
   *                 points, {{colors, {}, SkTileMode::kClamp}, {}}));
   *         canvas->drawRect(rect, newPaint);
   *
   *         SkRRect oval;
   *         oval.setOval(rect);
   *         oval.offset(40, 80);
   *         paint.setColor(0xFFE6B89C);
   *         canvas->drawRRect(oval, paint);
   *
   *         paint.setColor(0xFF9CAFB7);
   *         canvas->drawCircle(180, 50, 25, paint);
   *
   *         rect.offset(80, 50);
   *         paint.setColor(0xFF4281A4);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         canvas->drawRoundRect(rect, 10, 10, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
