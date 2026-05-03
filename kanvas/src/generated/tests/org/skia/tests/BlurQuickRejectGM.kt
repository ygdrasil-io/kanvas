package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class BlurQuickRejectGM : public skiagm::GM {
 * public:
 *     BlurQuickRejectGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("blurquickreject"); }
 *
 *     SkISize getISize() override { return SkISize::Make(kWidth, kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         constexpr SkScalar kBlurRadius = SkIntToScalar(20);
 *         constexpr SkScalar kBoxSize = SkIntToScalar(100);
 *
 *         SkRect clipRect = SkRect::MakeXYWH(0, 0, kBoxSize, kBoxSize);
 *         SkRect blurRects[] = {
 *             { -kBoxSize - (kBlurRadius+1), 0, -(kBlurRadius+1), kBoxSize },
 *             { 0, -kBoxSize - (kBlurRadius+1), kBoxSize, -(kBlurRadius+1) },
 *             { kBoxSize+kBlurRadius+1, 0, 2*kBoxSize+kBlurRadius+1, kBoxSize },
 *             { 0, kBoxSize+kBlurRadius+1, kBoxSize, 2*kBoxSize+kBlurRadius+1 }
 *         };
 *         SkColor colors[] = {
 *             SK_ColorRED,
 *             SK_ColorGREEN,
 *             SK_ColorBLUE,
 *             SK_ColorYELLOW,
 *         };
 *         SkASSERT(std::size(colors) == std::size(blurRects));
 *
 *         SkPaint hairlinePaint;
 *         hairlinePaint.setStyle(SkPaint::kStroke_Style);
 *         hairlinePaint.setColor(SK_ColorWHITE);
 *         hairlinePaint.setStrokeWidth(0);
 *
 *         SkPaint blurPaint;
 *         blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle,
 *                                                     SkBlurMask::ConvertRadiusToSigma(kBlurRadius)));
 *
 *         canvas->clear(SK_ColorBLACK);
 *         canvas->save();
 *         canvas->translate(kBoxSize, kBoxSize);
 *         canvas->drawRect(clipRect, hairlinePaint);
 *         canvas->clipRect(clipRect);
 *         for (size_t i = 0; i < std::size(blurRects); ++i) {
 *             blurPaint.setColor(colors[i]);
 *             canvas->drawRect(blurRects[i], blurPaint);
 *             canvas->drawRect(blurRects[i], hairlinePaint);
 *         }
 *         canvas->restore();
 *     }
 *
 * private:
 *     inline static constexpr int kWidth = 300;
 *     inline static constexpr int kHeight = 300;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class BlurQuickRejectGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("blurquickreject"); }
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
   *         constexpr SkScalar kBlurRadius = SkIntToScalar(20);
   *         constexpr SkScalar kBoxSize = SkIntToScalar(100);
   *
   *         SkRect clipRect = SkRect::MakeXYWH(0, 0, kBoxSize, kBoxSize);
   *         SkRect blurRects[] = {
   *             { -kBoxSize - (kBlurRadius+1), 0, -(kBlurRadius+1), kBoxSize },
   *             { 0, -kBoxSize - (kBlurRadius+1), kBoxSize, -(kBlurRadius+1) },
   *             { kBoxSize+kBlurRadius+1, 0, 2*kBoxSize+kBlurRadius+1, kBoxSize },
   *             { 0, kBoxSize+kBlurRadius+1, kBoxSize, 2*kBoxSize+kBlurRadius+1 }
   *         };
   *         SkColor colors[] = {
   *             SK_ColorRED,
   *             SK_ColorGREEN,
   *             SK_ColorBLUE,
   *             SK_ColorYELLOW,
   *         };
   *         SkASSERT(std::size(colors) == std::size(blurRects));
   *
   *         SkPaint hairlinePaint;
   *         hairlinePaint.setStyle(SkPaint::kStroke_Style);
   *         hairlinePaint.setColor(SK_ColorWHITE);
   *         hairlinePaint.setStrokeWidth(0);
   *
   *         SkPaint blurPaint;
   *         blurPaint.setMaskFilter(SkMaskFilter::MakeBlur(kNormal_SkBlurStyle,
   *                                                     SkBlurMask::ConvertRadiusToSigma(kBlurRadius)));
   *
   *         canvas->clear(SK_ColorBLACK);
   *         canvas->save();
   *         canvas->translate(kBoxSize, kBoxSize);
   *         canvas->drawRect(clipRect, hairlinePaint);
   *         canvas->clipRect(clipRect);
   *         for (size_t i = 0; i < std::size(blurRects); ++i) {
   *             blurPaint.setColor(colors[i]);
   *             canvas->drawRect(blurRects[i], blurPaint);
   *             canvas->drawRect(blurRects[i], hairlinePaint);
   *         }
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
