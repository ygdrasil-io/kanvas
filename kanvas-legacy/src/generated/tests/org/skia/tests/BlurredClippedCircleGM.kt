package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BlurredClippedCircleGM : public GM {
 * public:
 *     BlurredClippedCircleGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("blurredclippedcircle"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint whitePaint;
 *         whitePaint.setColor(SK_ColorWHITE);
 *         whitePaint.setBlendMode(SkBlendMode::kSrc);
 *         whitePaint.setAntiAlias(true);
 *
 *         // This scale exercises precision limits in the circle blur effect (crbug.com/560651)
 *         constexpr float kScale = 2.0f;
 *         canvas->scale(kScale, kScale);
 *
 *         canvas->save();
 *             SkRect clipRect1 = SkRect::MakeLTRB(0, 0, kWidth, kHeight);
 *             canvas->clipRect(clipRect1);
 *
 *             canvas->save();
 *
 *                 canvas->clipRect(clipRect1);
 *                 canvas->drawRect(clipRect1, whitePaint);
 *
 *                 canvas->save();
 *
 *                     SkRect clipRect2 = SkRect::MakeLTRB(8, 8, 288, 288);
 *                     SkRRect clipRRect = SkRRect::MakeOval(clipRect2);
 *                     canvas->clipRRect(clipRRect, SkClipOp::kDifference, true);
 *
 *                     SkRect r = SkRect::MakeLTRB(4, 4, 292, 292);
 *                     SkRRect rr = SkRRect::MakeOval(r);
 *
 *                     SkPaint paint;
 *
 *                     paint.setMaskFilter(SkMaskFilter::MakeBlur(
 *                                             kNormal_SkBlurStyle,
 *                                             1.366025f));
 *                     paint.setColorFilter(SkColorFilters::Blend(SK_ColorRED, SkBlendMode::kSrcIn));
 *                     paint.setAntiAlias(true);
 *
 *                     canvas->drawRRect(rr, paint);
 *
 *                 canvas->restore();
 *             canvas->restore();
 *         canvas->restore();
 *     }
 *
 * private:
 *     inline static constexpr int kWidth = 1164;
 *     inline static constexpr int kHeight = 802;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BlurredClippedCircleGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("blurredclippedcircle"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint whitePaint;
   *         whitePaint.setColor(SK_ColorWHITE);
   *         whitePaint.setBlendMode(SkBlendMode::kSrc);
   *         whitePaint.setAntiAlias(true);
   *
   *         // This scale exercises precision limits in the circle blur effect (crbug.com/560651)
   *         constexpr float kScale = 2.0f;
   *         canvas->scale(kScale, kScale);
   *
   *         canvas->save();
   *             SkRect clipRect1 = SkRect::MakeLTRB(0, 0, kWidth, kHeight);
   *             canvas->clipRect(clipRect1);
   *
   *             canvas->save();
   *
   *                 canvas->clipRect(clipRect1);
   *                 canvas->drawRect(clipRect1, whitePaint);
   *
   *                 canvas->save();
   *
   *                     SkRect clipRect2 = SkRect::MakeLTRB(8, 8, 288, 288);
   *                     SkRRect clipRRect = SkRRect::MakeOval(clipRect2);
   *                     canvas->clipRRect(clipRRect, SkClipOp::kDifference, true);
   *
   *                     SkRect r = SkRect::MakeLTRB(4, 4, 292, 292);
   *                     SkRRect rr = SkRRect::MakeOval(r);
   *
   *                     SkPaint paint;
   *
   *                     paint.setMaskFilter(SkMaskFilter::MakeBlur(
   *                                             kNormal_SkBlurStyle,
   *                                             1.366025f));
   *                     paint.setColorFilter(SkColorFilters::Blend(SK_ColorRED, SkBlendMode::kSrcIn));
   *                     paint.setAntiAlias(true);
   *
   *                     canvas->drawRRect(rr, paint);
   *
   *                 canvas->restore();
   *             canvas->restore();
   *         canvas->restore();
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kWidth: Int = TODO("Initialize kWidth")

    private val kHeight: Int = TODO("Initialize kHeight")
  }
}
