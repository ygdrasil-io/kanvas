package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageFiltersScaledGM : public GM {
 * public:
 *     ImageFiltersScaledGM() {
 *         this->setBGColor(0x00000000);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefiltersscaled"); }
 *
 *     SkISize getISize() override { return SkISize::Make(1428, 500); }
 *
 *     void onOnceBeforeDraw() override {
 *         fCheckerboard = ToolUtils::create_checkerboard_image(64, 64, 0xFFA0A0A0, 0xFF404040, 8);
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
 *
 *         SkPoint3 pointLocation = SkPoint3::Make(0, 0, SkIntToScalar(10));
 *         SkPoint3 spotLocation = SkPoint3::Make(SkIntToScalar(-10),
 *                                                SkIntToScalar(-10),
 *                                                SkIntToScalar(20));
 *         SkPoint3 spotTarget = SkPoint3::Make(SkIntToScalar(40), SkIntToScalar(40), 0);
 *         SkScalar spotExponent = SK_Scalar1;
 *         SkScalar cutoffAngle = SkIntToScalar(15);
 *         SkScalar kd = SkIntToScalar(2);
 *         SkScalar surfaceScale = SkIntToScalar(1);
 *         SkColor white(0xFFFFFFFF);
 *         SkMatrix resizeMatrix;
 *         resizeMatrix.setScale(RESIZE_FACTOR, RESIZE_FACTOR);
 *
 *         sk_sp<SkImageFilter> filters[] = {
 *                 SkImageFilters::Blur(SkIntToScalar(4), SkIntToScalar(4), nullptr),
 *                 SkImageFilters::DropShadow(5, 10, 3, 3, SK_ColorYELLOW, nullptr),
 *                 SkImageFilters::DisplacementMap(SkColorChannel::kR,
 *                                                 SkColorChannel::kR,
 *                                                 12,
 *                                                 std::move(gradient),
 *                                                 checkerboard),
 *                 SkImageFilters::Dilate(1, 1, checkerboard),
 *                 SkImageFilters::Erode(1, 1, checkerboard),
 *                 SkImageFilters::Offset(SkIntToScalar(32), 0, nullptr),
 *                 SkImageFilters::MatrixTransform(resizeMatrix, SkSamplingOptions(), nullptr),
 *                 SkImageFilters::Shader(SkShaders::MakeFractalNoise(
 *                         SkDoubleToScalar(0.1), SkDoubleToScalar(0.05), 1, 0)),
 *                 SkImageFilters::PointLitDiffuse(pointLocation, white, surfaceScale, kd, nullptr),
 *                 SkImageFilters::SpotLitDiffuse(spotLocation,
 *                                                spotTarget,
 *                                                spotExponent,
 *                                                cutoffAngle,
 *                                                white,
 *                                                surfaceScale,
 *                                                kd,
 *                                                nullptr),
 *         };
 *
 *         SkVector scales[] = {
 *             SkVector::Make(SkScalarInvert(2), SkScalarInvert(2)),
 *             SkVector::Make(SkIntToScalar(1), SkIntToScalar(1)),
 *             SkVector::Make(SkIntToScalar(1), SkIntToScalar(2)),
 *             SkVector::Make(SkIntToScalar(2), SkIntToScalar(1)),
 *             SkVector::Make(SkIntToScalar(2), SkIntToScalar(2)),
 *         };
 *
 *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
 *         SkScalar margin = SkIntToScalar(16);
 *         SkRect bounds = r;
 *         bounds.outset(margin, margin);
 *
 *         for (size_t j = 0; j < std::size(scales); ++j) {
 *             canvas->save();
 *             for (size_t i = 0; i < std::size(filters); ++i) {
 *                 SkPaint paint;
 *                 paint.setColor(SK_ColorBLUE);
 *                 paint.setImageFilter(filters[i]);
 *                 paint.setAntiAlias(true);
 *                 canvas->save();
 *                 canvas->scale(scales[j].fX, scales[j].fY);
 *                 canvas->clipRect(r);
 *                 if (5 == i) {
 *                     canvas->translate(SkIntToScalar(-32), 0);
 *                 } else if (6 == i) {
 *                     canvas->scale(SkScalarInvert(RESIZE_FACTOR),
 *                                   SkScalarInvert(RESIZE_FACTOR));
 *                 }
 *                 canvas->drawCircle(r.centerX(), r.centerY(), r.width()*2/5, paint);
 *                 canvas->restore();
 *                 canvas->translate(r.width() * scales[j].fX + margin, 0);
 *             }
 *             canvas->restore();
 *             canvas->translate(0, r.height() * scales[j].fY + margin);
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
public open class ImageFiltersScaledGM public constructor() : GM() {
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
   * SkString getName() const override { return SkString("imagefiltersscaled"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(1428, 500); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fCheckerboard = ToolUtils::create_checkerboard_image(64, 64, 0xFFA0A0A0, 0xFF404040, 8);
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
   *
   *         SkPoint3 pointLocation = SkPoint3::Make(0, 0, SkIntToScalar(10));
   *         SkPoint3 spotLocation = SkPoint3::Make(SkIntToScalar(-10),
   *                                                SkIntToScalar(-10),
   *                                                SkIntToScalar(20));
   *         SkPoint3 spotTarget = SkPoint3::Make(SkIntToScalar(40), SkIntToScalar(40), 0);
   *         SkScalar spotExponent = SK_Scalar1;
   *         SkScalar cutoffAngle = SkIntToScalar(15);
   *         SkScalar kd = SkIntToScalar(2);
   *         SkScalar surfaceScale = SkIntToScalar(1);
   *         SkColor white(0xFFFFFFFF);
   *         SkMatrix resizeMatrix;
   *         resizeMatrix.setScale(RESIZE_FACTOR, RESIZE_FACTOR);
   *
   *         sk_sp<SkImageFilter> filters[] = {
   *                 SkImageFilters::Blur(SkIntToScalar(4), SkIntToScalar(4), nullptr),
   *                 SkImageFilters::DropShadow(5, 10, 3, 3, SK_ColorYELLOW, nullptr),
   *                 SkImageFilters::DisplacementMap(SkColorChannel::kR,
   *                                                 SkColorChannel::kR,
   *                                                 12,
   *                                                 std::move(gradient),
   *                                                 checkerboard),
   *                 SkImageFilters::Dilate(1, 1, checkerboard),
   *                 SkImageFilters::Erode(1, 1, checkerboard),
   *                 SkImageFilters::Offset(SkIntToScalar(32), 0, nullptr),
   *                 SkImageFilters::MatrixTransform(resizeMatrix, SkSamplingOptions(), nullptr),
   *                 SkImageFilters::Shader(SkShaders::MakeFractalNoise(
   *                         SkDoubleToScalar(0.1), SkDoubleToScalar(0.05), 1, 0)),
   *                 SkImageFilters::PointLitDiffuse(pointLocation, white, surfaceScale, kd, nullptr),
   *                 SkImageFilters::SpotLitDiffuse(spotLocation,
   *                                                spotTarget,
   *                                                spotExponent,
   *                                                cutoffAngle,
   *                                                white,
   *                                                surfaceScale,
   *                                                kd,
   *                                                nullptr),
   *         };
   *
   *         SkVector scales[] = {
   *             SkVector::Make(SkScalarInvert(2), SkScalarInvert(2)),
   *             SkVector::Make(SkIntToScalar(1), SkIntToScalar(1)),
   *             SkVector::Make(SkIntToScalar(1), SkIntToScalar(2)),
   *             SkVector::Make(SkIntToScalar(2), SkIntToScalar(1)),
   *             SkVector::Make(SkIntToScalar(2), SkIntToScalar(2)),
   *         };
   *
   *         SkRect r = SkRect::MakeWH(SkIntToScalar(64), SkIntToScalar(64));
   *         SkScalar margin = SkIntToScalar(16);
   *         SkRect bounds = r;
   *         bounds.outset(margin, margin);
   *
   *         for (size_t j = 0; j < std::size(scales); ++j) {
   *             canvas->save();
   *             for (size_t i = 0; i < std::size(filters); ++i) {
   *                 SkPaint paint;
   *                 paint.setColor(SK_ColorBLUE);
   *                 paint.setImageFilter(filters[i]);
   *                 paint.setAntiAlias(true);
   *                 canvas->save();
   *                 canvas->scale(scales[j].fX, scales[j].fY);
   *                 canvas->clipRect(r);
   *                 if (5 == i) {
   *                     canvas->translate(SkIntToScalar(-32), 0);
   *                 } else if (6 == i) {
   *                     canvas->scale(SkScalarInvert(RESIZE_FACTOR),
   *                                   SkScalarInvert(RESIZE_FACTOR));
   *                 }
   *                 canvas->drawCircle(r.centerX(), r.centerY(), r.width()*2/5, paint);
   *                 canvas->restore();
   *                 canvas->translate(r.width() * scales[j].fX + margin, 0);
   *             }
   *             canvas->restore();
   *             canvas->translate(0, r.height() * scales[j].fY + margin);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
