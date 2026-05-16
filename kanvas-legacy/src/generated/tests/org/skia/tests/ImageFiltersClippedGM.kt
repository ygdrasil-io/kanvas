package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersClippedGM : public GM {
 * public:
 *     ImageFiltersClippedGM() {
 *         this->setBGColor(0x00000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefiltersclipped"); }
 *
 *     SkISize getISize() override { return SkISize::Make(860, 500); }
 *
 *     void onOnceBeforeDraw() override {
 *         fCheckerboard =
 *                 ToolUtils::create_checkerboard_image(64, 64, 0xFFA0A0A0, 0xFF404040, 8);
 *         fGradientCircle = make_gradient_circle(64, 64);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorBLACK);
 *
 *         sk_sp<SkImageFilter> gradient(SkImageFilters::Image(fGradientCircle,
 *                                                             SkFilterMode::kLinear));
 *         sk_sp<SkImageFilter> checkerboard(SkImageFilters::Image(fCheckerboard,
 *                                                                 SkFilterMode::kLinear));
 *         SkMatrix resizeMatrix;
 *         resizeMatrix.setScale(RESIZE_FACTOR_X, RESIZE_FACTOR_Y);
 *         SkPoint3 pointLocation = SkPoint3::Make(32, 32, SkIntToScalar(10));
 *
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
 *         sk_sp<SkImageFilter> filters[] = {
 *             SkImageFilters::Blur(SkIntToScalar(12), SkIntToScalar(12), nullptr),
 *             SkImageFilters::DropShadow(SkIntToScalar(10), SkIntToScalar(10),
 *                                        SkIntToScalar(3), SkIntToScalar(3), SK_ColorGREEN, nullptr),
 *             SkImageFilters::DisplacementMap(SkColorChannel::kR, SkColorChannel::kR,
 *                                             SkIntToScalar(12), std::move(gradient), checkerboard),
 *             SkImageFilters::Dilate(2, 2, checkerboard),
 *             SkImageFilters::Erode(2, 2, checkerboard),
 *             SkImageFilters::Offset(SkIntToScalar(-16), SkIntToScalar(32), nullptr),
 *             SkImageFilters::MatrixTransform(resizeMatrix, SkSamplingOptions(), nullptr),
 *             // Crop output of lighting to the checkerboard
 *             SkImageFilters::PointLitDiffuse(pointLocation, SK_ColorWHITE, SK_Scalar1,
 *                                             SkIntToScalar(2), checkerboard, r),
 *         };
 *
 *         SkScalar margin = SkIntToScalar(16);
 *         SkRect bounds = r;
 *         bounds.outset(margin, margin);
 *
 *         canvas->save();
 *         for (int xOffset = 0; xOffset < 80; xOffset += 16) {
 *             canvas->save();
 *             bounds.fLeft = SkIntToScalar(xOffset);
 *             for (size_t i = 0; i < std::size(filters); ++i) {
 *                 draw_clipped_filter(canvas, filters[i], i, r, bounds);
 *                 canvas->translate(r.width() + margin, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, r.height() + margin);
 *         }
 *         canvas->restore();
 *
 *         sk_sp<SkImageFilter> rectFilter(
 *                 SkImageFilters::Shader(SkShaders::MakeFractalNoise(0.1f, 0.05f, 1, 0)));
 *         canvas->translate(std::size(filters)*(r.width() + margin), 0);
 *         for (int xOffset = 0; xOffset < 80; xOffset += 16) {
 *             bounds.fLeft = SkIntToScalar(xOffset);
 *             draw_clipped_filter(canvas, rectFilter, 0, r, bounds);
 *             canvas->translate(0, r.height() + margin);
 *         }
 *     }
 *
 * private:
 *     sk_sp<SkImage> fCheckerboard, fGradientCircle;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageFiltersClippedGM public constructor() : GM() {
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
   * sk_sp<SkImage> fCheckerboard, fGradientCircle
   * ```
   */
  private var fGradientCircle: SkSp<SkImage> = TODO("Initialize fGradientCircle")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imagefiltersclipped"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(860, 500); }
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
   *         canvas->clear(SK_ColorBLACK);
   *
   *         sk_sp<SkImageFilter> gradient(SkImageFilters::Image(fGradientCircle,
   *                                                             SkFilterMode::kLinear));
   *         sk_sp<SkImageFilter> checkerboard(SkImageFilters::Image(fCheckerboard,
   *                                                                 SkFilterMode::kLinear));
   *         SkMatrix resizeMatrix;
   *         resizeMatrix.setScale(RESIZE_FACTOR_X, RESIZE_FACTOR_Y);
   *         SkPoint3 pointLocation = SkPoint3::Make(32, 32, SkIntToScalar(10));
   *
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
   *         sk_sp<SkImageFilter> filters[] = {
   *             SkImageFilters::Blur(SkIntToScalar(12), SkIntToScalar(12), nullptr),
   *             SkImageFilters::DropShadow(SkIntToScalar(10), SkIntToScalar(10),
   *                                        SkIntToScalar(3), SkIntToScalar(3), SK_ColorGREEN, nullptr),
   *             SkImageFilters::DisplacementMap(SkColorChannel::kR, SkColorChannel::kR,
   *                                             SkIntToScalar(12), std::move(gradient), checkerboard),
   *             SkImageFilters::Dilate(2, 2, checkerboard),
   *             SkImageFilters::Erode(2, 2, checkerboard),
   *             SkImageFilters::Offset(SkIntToScalar(-16), SkIntToScalar(32), nullptr),
   *             SkImageFilters::MatrixTransform(resizeMatrix, SkSamplingOptions(), nullptr),
   *             // Crop output of lighting to the checkerboard
   *             SkImageFilters::PointLitDiffuse(pointLocation, SK_ColorWHITE, SK_Scalar1,
   *                                             SkIntToScalar(2), checkerboard, r),
   *         };
   *
   *         SkScalar margin = SkIntToScalar(16);
   *         SkRect bounds = r;
   *         bounds.outset(margin, margin);
   *
   *         canvas->save();
   *         for (int xOffset = 0; xOffset < 80; xOffset += 16) {
   *             canvas->save();
   *             bounds.fLeft = SkIntToScalar(xOffset);
   *             for (size_t i = 0; i < std::size(filters); ++i) {
   *                 draw_clipped_filter(canvas, filters[i], i, r, bounds);
   *                 canvas->translate(r.width() + margin, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, r.height() + margin);
   *         }
   *         canvas->restore();
   *
   *         sk_sp<SkImageFilter> rectFilter(
   *                 SkImageFilters::Shader(SkShaders::MakeFractalNoise(0.1f, 0.05f, 1, 0)));
   *         canvas->translate(std::size(filters)*(r.width() + margin), 0);
   *         for (int xOffset = 0; xOffset < 80; xOffset += 16) {
   *             bounds.fLeft = SkIntToScalar(xOffset);
   *             draw_clipped_filter(canvas, rectFilter, 0, r, bounds);
   *             canvas->translate(0, r.height() + margin);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
