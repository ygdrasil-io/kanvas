package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkRegion
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class DrawRegionModesGM : public skiagm::GM {
 * public:
 *     DrawRegionModesGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("drawregionmodes"); }
 *
 *     SkISize getISize() override { return SkISize::Make(375, 500); }
 *
 *     void onOnceBeforeDraw() override {
 *         fRegion.op({50,  50, 100, 100}, SkRegion::kUnion_Op);
 *         fRegion.op({50, 100, 150, 150}, SkRegion::kUnion_Op);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorGREEN);
 *
 *         SkPaint paint;
 *         paint.setStyle(SkPaint::kFill_Style);
 *         paint.setColor(SK_ColorRED);
 *         paint.setAntiAlias(true);
 *
 *         canvas->save();
 *         canvas->translate(-50.0f, 75.0f);
 *         canvas->rotate(-45.0f);
 *         canvas->drawRegion(fRegion, paint);
 *
 *         canvas->translate(125.0f, 125.0f);
 *         paint.setImageFilter(SkImageFilters::Blur(5.0f, 5.0f, nullptr, nullptr));
 *         canvas->drawRegion(fRegion, paint);
 *
 *         canvas->translate(-125.0f, 125.0f);
 *         paint.setImageFilter(nullptr);
 *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 5.0f));
 *         canvas->drawRegion(fRegion, paint);
 *
 *         canvas->translate(-125.0f, -125.0f);
 *         paint.setMaskFilter(nullptr);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         float intervals[] = { 5.0f, 5.0f };
 *         paint.setPathEffect(SkDashPathEffect::Make(intervals, 2.5f));
 *         canvas->drawRegion(fRegion, paint);
 *
 *         canvas->restore();
 *
 *         canvas->translate(100, 325);
 *         paint.setPathEffect(nullptr);
 *         paint.setStyle(SkPaint::kFill_Style);
 *         const SkPoint points[] = { SkPoint::Make(50.0f, 50.0f), SkPoint::Make(150.0f, 150.0f) };
 *         const SkColor4f colors[] = { SkColors::kBlue, SkColors::kYellow };
 *         paint.setShader(SkShaders::LinearGradient(points, {{colors, {}, SkTileMode::kClamp}, {}}));
 *         canvas->drawRegion(fRegion, paint);
 *     }
 *
 * private:
 *     SkRegion fRegion;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class DrawRegionModesGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkRegion fRegion
   * ```
   */
  private var fRegion: SkRegion = TODO("Initialize fRegion")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("drawregionmodes"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(375, 500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fRegion.op({50,  50, 100, 100}, SkRegion::kUnion_Op);
   *         fRegion.op({50, 100, 150, 150}, SkRegion::kUnion_Op);
   *     }
   * ```
   */
  protected override fun onOnceBeforeDraw() {
    TODO("Implement onOnceBeforeDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clear(SK_ColorGREEN);
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kFill_Style);
   *         paint.setColor(SK_ColorRED);
   *         paint.setAntiAlias(true);
   *
   *         canvas->save();
   *         canvas->translate(-50.0f, 75.0f);
   *         canvas->rotate(-45.0f);
   *         canvas->drawRegion(fRegion, paint);
   *
   *         canvas->translate(125.0f, 125.0f);
   *         paint.setImageFilter(SkImageFilters::Blur(5.0f, 5.0f, nullptr, nullptr));
   *         canvas->drawRegion(fRegion, paint);
   *
   *         canvas->translate(-125.0f, 125.0f);
   *         paint.setImageFilter(nullptr);
   *         paint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle, 5.0f));
   *         canvas->drawRegion(fRegion, paint);
   *
   *         canvas->translate(-125.0f, -125.0f);
   *         paint.setMaskFilter(nullptr);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         float intervals[] = { 5.0f, 5.0f };
   *         paint.setPathEffect(SkDashPathEffect::Make(intervals, 2.5f));
   *         canvas->drawRegion(fRegion, paint);
   *
   *         canvas->restore();
   *
   *         canvas->translate(100, 325);
   *         paint.setPathEffect(nullptr);
   *         paint.setStyle(SkPaint::kFill_Style);
   *         const SkPoint points[] = { SkPoint::Make(50.0f, 50.0f), SkPoint::Make(150.0f, 150.0f) };
   *         const SkColor4f colors[] = { SkColors::kBlue, SkColors::kYellow };
   *         paint.setShader(SkShaders::LinearGradient(points, {{colors, {}, SkTileMode::kClamp}, {}}));
   *         canvas->drawRegion(fRegion, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
