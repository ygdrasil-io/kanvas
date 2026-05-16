package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersGraphGM : public skiagm::GM {
 * public:
 *     ImageFiltersGraphGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefiltersgraph"); }
 *
 *     SkISize getISize() override { return SkISize::Make(600, 150); }
 *
 *     void onOnceBeforeDraw() override {
 *         fImage = ToolUtils::CreateStringImage(100, 100, SK_ColorWHITE, 20, 70, 96, "e");
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorBLACK);
 *         {
 *             sk_sp<SkImageFilter> bitmapSource(SkImageFilters::Image(fImage, SkFilterMode::kLinear));
 *             sk_sp<SkColorFilter> cf(SkColorFilters::Blend(SK_ColorRED,
 *                                                                   SkBlendMode::kSrcIn));
 *             sk_sp<SkImageFilter> blur(SkImageFilters::Blur(4.0f, 4.0f, std::move(bitmapSource)));
 *             sk_sp<SkImageFilter> erode(SkImageFilters::Erode(4, 4, blur));
 *             sk_sp<SkImageFilter> color(SkImageFilters::ColorFilter(std::move(cf),
 *                                                                       std::move(erode)));
 *             sk_sp<SkImageFilter> merge(SkImageFilters::Merge(blur, color));
 *
 *             SkPaint paint;
 *             paint.setImageFilter(std::move(merge));
 *             canvas->drawPaint(paint);
 *             canvas->translate(SkIntToScalar(100), 0);
 *         }
 *         {
 *             sk_sp<SkImageFilter> morph(SkImageFilters::Dilate(5, 5, nullptr));
 *
 *             float matrix[20] = { 1, 0, 0, 0, 0,
 *                                  0, 1, 0, 0, 0,
 *                                  0, 0, 1, 0, 0,
 *                                  0, 0, 0, 0.5f, 0 };
 *
 *             sk_sp<SkColorFilter> matrixFilter(SkColorFilters::Matrix(matrix));
 *             sk_sp<SkImageFilter> colorMorph(SkImageFilters::ColorFilter(std::move(matrixFilter),
 *                                                                            std::move(morph)));
 *             SkPaint paint;
 *             paint.setImageFilter(SkImageFilters::Blend(SkBlendMode::kSrcOver,
 *                                                        std::move(colorMorph)));
 *
 *             DrawClippedImage(canvas, fImage.get(), paint);
 *             canvas->translate(SkIntToScalar(100), 0);
 *         }
 *         {
 *             float matrix[20] = { 1, 0, 0, 0, 0,
 *                                  0, 1, 0, 0, 0,
 *                                  0, 0, 1, 0, 0,
 *                                  0, 0, 0, 0.5f, 0 };
 *             sk_sp<SkColorFilter> matrixCF(SkColorFilters::Matrix(matrix));
 *             sk_sp<SkImageFilter> matrixFilter(SkImageFilters::ColorFilter(std::move(matrixCF),
 *                                                                              nullptr));
 *             sk_sp<SkImageFilter> offsetFilter(SkImageFilters::Offset(10.0f, 10.f, matrixFilter));
 *
 *             SkPaint paint;
 *             paint.setImageFilter(SkImageFilters::Arithmetic(
 *                     0, 1, 1, 0, true, std::move(matrixFilter), std::move(offsetFilter), nullptr));
 *
 *             DrawClippedImage(canvas, fImage.get(), paint);
 *             canvas->translate(SkIntToScalar(100), 0);
 *         }
 *         {
 *             sk_sp<SkImageFilter> blur(SkImageFilters::Blur(10, 10, nullptr));
 *
 *             SkIRect cropRect = SkIRect::MakeWH(95, 100);
 *             SkPaint paint;
 *             paint.setImageFilter(
 *                 SkImageFilters::Blend(SkBlendMode::kSrcIn, std::move(blur), nullptr, &cropRect));
 *             DrawClippedImage(canvas, fImage.get(), paint);
 *             canvas->translate(SkIntToScalar(100), 0);
 *         }
 *         {
 *             // Dilate -> matrix convolution.
 *             // This tests that a filter using asFragmentProcessor (matrix
 *             // convolution) correctly handles a non-zero source offset
 *             // (supplied by the dilate).
 *             sk_sp<SkImageFilter> dilate(SkImageFilters::Dilate(5, 5, nullptr));
 *
 *             SkScalar kernel[9] = { -1, -1, -1,
 *                                    -1,  7, -1,
 *                                    -1, -1, -1 };
 *             SkISize kernelSize = SkISize::Make(3, 3);
 *             SkScalar gain = 1.0f, bias = 0;
 *             SkIPoint kernelOffset = SkIPoint::Make(1, 1);
 *             bool convolveAlpha = false;
 *             sk_sp<SkImageFilter> convolve(SkImageFilters::MatrixConvolution(
 *                     kernelSize, kernel, gain, bias, kernelOffset, SkTileMode::kClamp, convolveAlpha,
 *                     std::move(dilate)));
 *
 *             SkPaint paint;
 *             paint.setImageFilter(std::move(convolve));
 *             DrawClippedImage(canvas, fImage.get(), paint);
 *             canvas->translate(SkIntToScalar(100), 0);
 *         }
 *         {
 *             // Test that crop offsets are absolute, not relative to the parent's crop rect.
 *             sk_sp<SkColorFilter> cf1(SkColorFilters::Blend(SK_ColorBLUE, SkBlendMode::kSrcIn));
 *             sk_sp<SkColorFilter> cf2(SkColorFilters::Blend(SK_ColorGREEN, SkBlendMode::kSrcIn));
 *             SkIRect outerRect = SkIRect::MakeXYWH(10, 10, 80, 80);
 *             SkIRect innerRect= SkIRect::MakeXYWH(20, 20, 60, 60);
 *             sk_sp<SkImageFilter> color1(SkImageFilters::ColorFilter(
 *                     std::move(cf1), nullptr, &outerRect));
 *             sk_sp<SkImageFilter> color2(SkImageFilters::ColorFilter(
 *                     std::move(cf2), std::move(color1),  &innerRect));
 *
 *             SkPaint paint;
 *             paint.setImageFilter(std::move(color2));
 *             paint.setColor(SK_ColorRED);
 *             canvas->drawRect(SkRect::MakeXYWH(0, 0, 100, 100), paint);
 *             canvas->translate(SkIntToScalar(100), 0);
 *         }
 *     }
 *
 * private:
 *     static void DrawClippedImage(SkCanvas* canvas, const SkImage* image, const SkPaint& paint) {
 *         canvas->save();
 *         canvas->clipIRect(image->bounds());
 *         canvas->drawImage(image, 0, 0, SkSamplingOptions(), &paint);
 *         canvas->restore();
 *     }
 *
 *     sk_sp<SkImage> fImage;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageFiltersGraphGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imagefiltersgraph"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(600, 150); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fImage = ToolUtils::CreateStringImage(100, 100, SK_ColorWHITE, 20, 70, 96, "e");
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
   *         canvas->clear(SK_ColorBLACK);
   *         {
   *             sk_sp<SkImageFilter> bitmapSource(SkImageFilters::Image(fImage, SkFilterMode::kLinear));
   *             sk_sp<SkColorFilter> cf(SkColorFilters::Blend(SK_ColorRED,
   *                                                                   SkBlendMode::kSrcIn));
   *             sk_sp<SkImageFilter> blur(SkImageFilters::Blur(4.0f, 4.0f, std::move(bitmapSource)));
   *             sk_sp<SkImageFilter> erode(SkImageFilters::Erode(4, 4, blur));
   *             sk_sp<SkImageFilter> color(SkImageFilters::ColorFilter(std::move(cf),
   *                                                                       std::move(erode)));
   *             sk_sp<SkImageFilter> merge(SkImageFilters::Merge(blur, color));
   *
   *             SkPaint paint;
   *             paint.setImageFilter(std::move(merge));
   *             canvas->drawPaint(paint);
   *             canvas->translate(SkIntToScalar(100), 0);
   *         }
   *         {
   *             sk_sp<SkImageFilter> morph(SkImageFilters::Dilate(5, 5, nullptr));
   *
   *             float matrix[20] = { 1, 0, 0, 0, 0,
   *                                  0, 1, 0, 0, 0,
   *                                  0, 0, 1, 0, 0,
   *                                  0, 0, 0, 0.5f, 0 };
   *
   *             sk_sp<SkColorFilter> matrixFilter(SkColorFilters::Matrix(matrix));
   *             sk_sp<SkImageFilter> colorMorph(SkImageFilters::ColorFilter(std::move(matrixFilter),
   *                                                                            std::move(morph)));
   *             SkPaint paint;
   *             paint.setImageFilter(SkImageFilters::Blend(SkBlendMode::kSrcOver,
   *                                                        std::move(colorMorph)));
   *
   *             DrawClippedImage(canvas, fImage.get(), paint);
   *             canvas->translate(SkIntToScalar(100), 0);
   *         }
   *         {
   *             float matrix[20] = { 1, 0, 0, 0, 0,
   *                                  0, 1, 0, 0, 0,
   *                                  0, 0, 1, 0, 0,
   *                                  0, 0, 0, 0.5f, 0 };
   *             sk_sp<SkColorFilter> matrixCF(SkColorFilters::Matrix(matrix));
   *             sk_sp<SkImageFilter> matrixFilter(SkImageFilters::ColorFilter(std::move(matrixCF),
   *                                                                              nullptr));
   *             sk_sp<SkImageFilter> offsetFilter(SkImageFilters::Offset(10.0f, 10.f, matrixFilter));
   *
   *             SkPaint paint;
   *             paint.setImageFilter(SkImageFilters::Arithmetic(
   *                     0, 1, 1, 0, true, std::move(matrixFilter), std::move(offsetFilter), nullptr));
   *
   *             DrawClippedImage(canvas, fImage.get(), paint);
   *             canvas->translate(SkIntToScalar(100), 0);
   *         }
   *         {
   *             sk_sp<SkImageFilter> blur(SkImageFilters::Blur(10, 10, nullptr));
   *
   *             SkIRect cropRect = SkIRect::MakeWH(95, 100);
   *             SkPaint paint;
   *             paint.setImageFilter(
   *                 SkImageFilters::Blend(SkBlendMode::kSrcIn, std::move(blur), nullptr, &cropRect));
   *             DrawClippedImage(canvas, fImage.get(), paint);
   *             canvas->translate(SkIntToScalar(100), 0);
   *         }
   *         {
   *             // Dilate -> matrix convolution.
   *             // This tests that a filter using asFragmentProcessor (matrix
   *             // convolution) correctly handles a non-zero source offset
   *             // (supplied by the dilate).
   *             sk_sp<SkImageFilter> dilate(SkImageFilters::Dilate(5, 5, nullptr));
   *
   *             SkScalar kernel[9] = { -1, -1, -1,
   *                                    -1,  7, -1,
   *                                    -1, -1, -1 };
   *             SkISize kernelSize = SkISize::Make(3, 3);
   *             SkScalar gain = 1.0f, bias = 0;
   *             SkIPoint kernelOffset = SkIPoint::Make(1, 1);
   *             bool convolveAlpha = false;
   *             sk_sp<SkImageFilter> convolve(SkImageFilters::MatrixConvolution(
   *                     kernelSize, kernel, gain, bias, kernelOffset, SkTileMode::kClamp, convolveAlpha,
   *                     std::move(dilate)));
   *
   *             SkPaint paint;
   *             paint.setImageFilter(std::move(convolve));
   *             DrawClippedImage(canvas, fImage.get(), paint);
   *             canvas->translate(SkIntToScalar(100), 0);
   *         }
   *         {
   *             // Test that crop offsets are absolute, not relative to the parent's crop rect.
   *             sk_sp<SkColorFilter> cf1(SkColorFilters::Blend(SK_ColorBLUE, SkBlendMode::kSrcIn));
   *             sk_sp<SkColorFilter> cf2(SkColorFilters::Blend(SK_ColorGREEN, SkBlendMode::kSrcIn));
   *             SkIRect outerRect = SkIRect::MakeXYWH(10, 10, 80, 80);
   *             SkIRect innerRect= SkIRect::MakeXYWH(20, 20, 60, 60);
   *             sk_sp<SkImageFilter> color1(SkImageFilters::ColorFilter(
   *                     std::move(cf1), nullptr, &outerRect));
   *             sk_sp<SkImageFilter> color2(SkImageFilters::ColorFilter(
   *                     std::move(cf2), std::move(color1),  &innerRect));
   *
   *             SkPaint paint;
   *             paint.setImageFilter(std::move(color2));
   *             paint.setColor(SK_ColorRED);
   *             canvas->drawRect(SkRect::MakeXYWH(0, 0, 100, 100), paint);
   *             canvas->translate(SkIntToScalar(100), 0);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void DrawClippedImage(SkCanvas* canvas, const SkImage* image, const SkPaint& paint) {
     *         canvas->save();
     *         canvas->clipIRect(image->bounds());
     *         canvas->drawImage(image, 0, 0, SkSamplingOptions(), &paint);
     *         canvas->restore();
     *     }
     * ```
     */
    private fun drawClippedImage(
      canvas: SkCanvas?,
      image: SkImage?,
      paint: SkPaint,
    ) {
      TODO("Implement drawClippedImage")
    }
  }
}
