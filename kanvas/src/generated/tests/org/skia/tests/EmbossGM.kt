package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EmbossGM : public skiagm::GM {
 * public:
 *     EmbossGM() {
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("emboss"); }
 *
 *     SkISize getISize() override { return SkISize::Make(600, 120); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkPaint paint;
 *         auto img = make_bm();
 *         canvas->drawImage(img, 10, 10);
 *         canvas->translate(img->width() + SkIntToScalar(10), 0);
 *
 *         paint.setMaskFilter(SkEmbossMaskFilter::Make(
 *             SkBlurMask::ConvertRadiusToSigma(3),
 *             { { SK_Scalar1, SK_Scalar1, SK_Scalar1 }, 0, 128, 16*2 }));
 *         canvas->drawImage(img, 10, 10, SkSamplingOptions(), &paint);
 *         canvas->translate(img->width() + SkIntToScalar(10), 0);
 *
 *         // this combination of emboss+colorfilter used to crash -- so we exercise it to
 *         // confirm that we have a fix.
 *         paint.setColorFilter(SkColorFilters::Blend(0xFFFF0000, SkBlendMode::kSrcATop));
 *         canvas->drawImage(img, 10, 10, SkSamplingOptions(), &paint);
 *         canvas->translate(img->width() + SkIntToScalar(10), 0);
 *
 *         paint.setAntiAlias(true);
 *         paint.setStyle(SkPaint::kStroke_Style);
 *         paint.setStrokeWidth(SkIntToScalar(10));
 *         paint.setMaskFilter(SkEmbossMaskFilter::Make(
 *             SkBlurMask::ConvertRadiusToSigma(4),
 *             { { SK_Scalar1, SK_Scalar1, SK_Scalar1 }, 0, 128, 16*2 }));
 *         paint.setColorFilter(nullptr);
 *         paint.setShader(SkShaders::Color(SK_ColorBLUE));
 *         paint.setDither(true);
 *         canvas->drawCircle(SkIntToScalar(50), SkIntToScalar(50),
 *                            SkIntToScalar(30), paint);
 *         canvas->translate(SkIntToScalar(100), 0);
 *
 *         SkFont font = SkFont(ToolUtils::DefaultPortableTypeface(), 50);
 *         paint.setStyle(SkPaint::kFill_Style);
 *         canvas->drawString("Hello", 0, 50, font, paint);
 *
 *         paint.setShader(nullptr);
 *         paint.setColor(SK_ColorGREEN);
 *         canvas->drawString("World", 0, 100, font, paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class EmbossGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("emboss"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(600, 120); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkPaint paint;
   *         auto img = make_bm();
   *         canvas->drawImage(img, 10, 10);
   *         canvas->translate(img->width() + SkIntToScalar(10), 0);
   *
   *         paint.setMaskFilter(SkEmbossMaskFilter::Make(
   *             SkBlurMask::ConvertRadiusToSigma(3),
   *             { { SK_Scalar1, SK_Scalar1, SK_Scalar1 }, 0, 128, 16*2 }));
   *         canvas->drawImage(img, 10, 10, SkSamplingOptions(), &paint);
   *         canvas->translate(img->width() + SkIntToScalar(10), 0);
   *
   *         // this combination of emboss+colorfilter used to crash -- so we exercise it to
   *         // confirm that we have a fix.
   *         paint.setColorFilter(SkColorFilters::Blend(0xFFFF0000, SkBlendMode::kSrcATop));
   *         canvas->drawImage(img, 10, 10, SkSamplingOptions(), &paint);
   *         canvas->translate(img->width() + SkIntToScalar(10), 0);
   *
   *         paint.setAntiAlias(true);
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(SkIntToScalar(10));
   *         paint.setMaskFilter(SkEmbossMaskFilter::Make(
   *             SkBlurMask::ConvertRadiusToSigma(4),
   *             { { SK_Scalar1, SK_Scalar1, SK_Scalar1 }, 0, 128, 16*2 }));
   *         paint.setColorFilter(nullptr);
   *         paint.setShader(SkShaders::Color(SK_ColorBLUE));
   *         paint.setDither(true);
   *         canvas->drawCircle(SkIntToScalar(50), SkIntToScalar(50),
   *                            SkIntToScalar(30), paint);
   *         canvas->translate(SkIntToScalar(100), 0);
   *
   *         SkFont font = SkFont(ToolUtils::DefaultPortableTypeface(), 50);
   *         paint.setStyle(SkPaint::kFill_Style);
   *         canvas->drawString("Hello", 0, 50, font, paint);
   *
   *         paint.setShader(nullptr);
   *         paint.setColor(SK_ColorGREEN);
   *         canvas->drawString("World", 0, 100, font, paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
