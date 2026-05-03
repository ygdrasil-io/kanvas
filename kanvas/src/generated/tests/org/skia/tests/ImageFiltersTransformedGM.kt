package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersTransformedGM : public GM {
 * public:
 *     ImageFiltersTransformedGM() {
 *         this->setBGColor(SK_ColorBLACK);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefilterstransformed"); }
 *
 *     SkISize getISize() override { return SkISize::Make(420, 240); }
 *
 *     void onOnceBeforeDraw() override {
 *         fCheckerboard =
 *                 ToolUtils::create_checkerboard_image(64, 64, 0xFFA0A0A0, 0xFF404040, 8);
 *         fGradientCircle = make_gradient_circle(64, 64);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         sk_sp<SkImageFilter> gradient(SkImageFilters::Image(fGradientCircle,
 *                                                             SkFilterMode::kLinear));
 *         sk_sp<SkImageFilter> checkerboard(SkImageFilters::Image(fCheckerboard,
 *                                                                 SkFilterMode::kLinear));
 *         sk_sp<SkImageFilter> filters[] = {
 *             SkImageFilters::Blur(12, 0, nullptr),
 *             SkImageFilters::DropShadow(0, 15, 8, 0, SK_ColorGREEN, nullptr),
 *             SkImageFilters::DisplacementMap(SkColorChannel::kR, SkColorChannel::kR, 12,
 *                                             std::move(gradient), checkerboard),
 *             SkImageFilters::Dilate(2, 2, checkerboard),
 *             SkImageFilters::Erode(2, 2, checkerboard),
 *         };
 *
 *         const SkScalar margin = SkIntToScalar(20);
 *         const SkScalar size = SkIntToScalar(60);
 *
 *         for (size_t j = 0; j < 3; j++) {
 *             canvas->save();
 *             canvas->translate(margin, 0);
 *             for (size_t i = 0; i < std::size(filters); ++i) {
 *                 SkPaint paint;
 *                 paint.setColor(SK_ColorWHITE);
 *                 paint.setImageFilter(filters[i]);
 *                 paint.setAntiAlias(true);
 *                 canvas->save();
 *                 canvas->translate(size * SK_ScalarHalf, size * SK_ScalarHalf);
 *                 canvas->scale(SkDoubleToScalar(0.8), SkDoubleToScalar(0.8));
 *                 if (j == 1) {
 *                     canvas->rotate(SkIntToScalar(45));
 *                 } else if (j == 2) {
 *                     canvas->skew(SkDoubleToScalar(0.5), SkDoubleToScalar(0.2));
 *                 }
 *                 canvas->translate(-size * SK_ScalarHalf, -size * SK_ScalarHalf);
 *                 canvas->drawOval(SkRect::MakeXYWH(0, size * SkDoubleToScalar(0.1),
 *                                                   size, size * SkDoubleToScalar(0.6)), paint);
 *                 canvas->restore();
 *                 canvas->translate(size + margin, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, size + margin);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkImage> fCheckerboard;
 *     sk_sp<SkImage> fGradientCircle;
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageFiltersTransformedGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fCheckerboard
   * ```
   */
  private var fCheckerboard: SkSp<SkImage> = TODO("Initialize fCheckerboard")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fGradientCircle
   * ```
   */
  private var fGradientCircle: SkSp<SkImage> = TODO("Initialize fGradientCircle")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imagefilterstransformed"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(420, 240); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fCheckerboard =
   *                 ToolUtils::create_checkerboard_image(64, 64, 0xFFA0A0A0, 0xFF404040, 8);
   *         fGradientCircle = make_gradient_circle(64, 64);
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
   *         sk_sp<SkImageFilter> gradient(SkImageFilters::Image(fGradientCircle,
   *                                                             SkFilterMode::kLinear));
   *         sk_sp<SkImageFilter> checkerboard(SkImageFilters::Image(fCheckerboard,
   *                                                                 SkFilterMode::kLinear));
   *         sk_sp<SkImageFilter> filters[] = {
   *             SkImageFilters::Blur(12, 0, nullptr),
   *             SkImageFilters::DropShadow(0, 15, 8, 0, SK_ColorGREEN, nullptr),
   *             SkImageFilters::DisplacementMap(SkColorChannel::kR, SkColorChannel::kR, 12,
   *                                             std::move(gradient), checkerboard),
   *             SkImageFilters::Dilate(2, 2, checkerboard),
   *             SkImageFilters::Erode(2, 2, checkerboard),
   *         };
   *
   *         const SkScalar margin = SkIntToScalar(20);
   *         const SkScalar size = SkIntToScalar(60);
   *
   *         for (size_t j = 0; j < 3; j++) {
   *             canvas->save();
   *             canvas->translate(margin, 0);
   *             for (size_t i = 0; i < std::size(filters); ++i) {
   *                 SkPaint paint;
   *                 paint.setColor(SK_ColorWHITE);
   *                 paint.setImageFilter(filters[i]);
   *                 paint.setAntiAlias(true);
   *                 canvas->save();
   *                 canvas->translate(size * SK_ScalarHalf, size * SK_ScalarHalf);
   *                 canvas->scale(SkDoubleToScalar(0.8), SkDoubleToScalar(0.8));
   *                 if (j == 1) {
   *                     canvas->rotate(SkIntToScalar(45));
   *                 } else if (j == 2) {
   *                     canvas->skew(SkDoubleToScalar(0.5), SkDoubleToScalar(0.2));
   *                 }
   *                 canvas->translate(-size * SK_ScalarHalf, -size * SK_ScalarHalf);
   *                 canvas->drawOval(SkRect::MakeXYWH(0, size * SkDoubleToScalar(0.1),
   *                                                   size, size * SkDoubleToScalar(0.6)), paint);
   *                 canvas->restore();
   *                 canvas->translate(size + margin, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, size + margin);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
