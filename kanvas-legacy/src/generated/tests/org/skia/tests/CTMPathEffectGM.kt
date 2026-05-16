package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class CTMPathEffectGM : public skiagm::GM {
 * protected:
 *     SkString getName() const override { return SkString("ctmpatheffect"); }
 *
 *     SkISize getISize() override { return SkISize::Make(800, 600); }
 *
 * #if defined(SK_GANESH)
 *     // CTM-aware path effects are not supported by Ganesh
 *     DrawResult onGpuSetup(SkCanvas* canvas, SkString*, GraphiteTestContext*) override {
 *         auto dctx = GrAsDirectContext(canvas->recordingContext());
 *         return dctx == nullptr ? DrawResult::kOk : DrawResult::kSkip;
 *     }
 * #endif
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const float strokeWidth = 16;
 *         const float pxInflate = 0.5f;
 *         sk_sp<SkPathEffect> pathEffect(new StrokeLineInflated(strokeWidth, pxInflate));
 *
 *         SkPath path = SkPath::Line({100, 100}, {200, 200});
 *
 *         // Draw the inflated path, and a scaled version, in blue.
 *         SkPaint paint;
 *         paint.setAntiAlias(true);
 *         paint.setColor(SkColorSetA(SK_ColorBLUE, 0xff));
 *         paint.setPathEffect(pathEffect);
 *         canvas->drawPath(path, paint);
 *         canvas->save();
 *         canvas->translate(150, 0);
 *         canvas->scale(2.5, 0.5f);
 *         canvas->drawPath(path, paint);
 *         canvas->restore();
 *
 *         // Draw the regular stroked version on top in green.
 *         // The inflated version should be visible underneath as a blue "border".
 *         paint.setPathEffect(nullptr);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(strokeWidth);
 *         paint.setColor(SkColorSetA(SK_ColorGREEN, 0xff));
 *         canvas->drawPath(path, paint);
 *         canvas->save();
 *         canvas->translate(150, 0);
 *         canvas->scale(2.5, 0.5f);
 *         canvas->drawPath(path, paint);
 *         canvas->restore();
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class CTMPathEffectGM : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("ctmpatheffect"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(800, 600); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const float strokeWidth = 16;
   *         const float pxInflate = 0.5f;
   *         sk_sp<SkPathEffect> pathEffect(new StrokeLineInflated(strokeWidth, pxInflate));
   *
   *         SkPath path = SkPath::Line({100, 100}, {200, 200});
   *
   *         // Draw the inflated path, and a scaled version, in blue.
   *         SkPaint paint;
   *         paint.setAntiAlias(true);
   *         paint.setColor(SkColorSetA(SK_ColorBLUE, 0xff));
   *         paint.setPathEffect(pathEffect);
   *         canvas->drawPath(path, paint);
   *         canvas->save();
   *         canvas->translate(150, 0);
   *         canvas->scale(2.5, 0.5f);
   *         canvas->drawPath(path, paint);
   *         canvas->restore();
   *
   *         // Draw the regular stroked version on top in green.
   *         // The inflated version should be visible underneath as a blue "border".
   *         paint.setPathEffect(nullptr);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(strokeWidth);
   *         paint.setColor(SkColorSetA(SK_ColorGREEN, 0xff));
   *         canvas->drawPath(path, paint);
   *         canvas->save();
   *         canvas->translate(150, 0);
   *         canvas->scale(2.5, 0.5f);
   *         canvas->drawPath(path, paint);
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
