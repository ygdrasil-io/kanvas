package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ImageFilterComposedTransform : public skiagm::GM {
 * public:
 *
 *     // Start at 70 degrees since that highlighted the issue in skbug.com/40042261
 *     ImageFilterComposedTransform() : fDegrees(70.f) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefilter_composed_transform"); }
 *
 *     SkISize getISize() override { return SkISize::Make(512, 512); }
 *
 *     bool onAnimate(double nanos) override {
 *         // Animate the rotation angle to test a variety of transformations
 *         fDegrees = TimeUtils::Scaled(1e-9f * nanos, 360.f);
 *         return true;
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fImage = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkMatrix matrix = SkMatrix::RotateDeg(fDegrees);
 *         // All four quadrants should render the same
 *         this->drawFilter(canvas, 0.f, 0.f, this->makeDirectFilter(matrix));
 *         this->drawFilter(canvas, 256.f, 0.f, this->makeEarlyComposeFilter(matrix));
 *         this->drawFilter(canvas, 0.f, 256.f, this->makeLateComposeFilter(matrix));
 *         this->drawFilter(canvas, 256.f, 256.f, this->makeFullComposeFilter(matrix));
 *     }
 *
 * private:
 *     SkScalar fDegrees;
 *     sk_sp<SkImage> fImage;
 *
 *     void drawFilter(SkCanvas* canvas, SkScalar tx, SkScalar ty, sk_sp<SkImageFilter> filter) const {
 *         SkPaint p;
 *         p.setImageFilter(std::move(filter));
 *
 *         canvas->save();
 *         canvas->translate(tx, ty);
 *         canvas->clipRect(SkRect::MakeWH(256, 256));
 *         canvas->scale(0.5f, 0.5f);
 *         canvas->translate(128, 128);
 *         canvas->drawImage(fImage, 0, 0, SkSamplingOptions(SkFilterMode::kLinear), &p);
 *         canvas->restore();
 *     }
 *
 *     // offset(matrix(offset))
 *     sk_sp<SkImageFilter> makeDirectFilter(const SkMatrix& matrix) const {
 *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
 *         sk_sp<SkImageFilter> filter = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
 *         filter = SkImageFilters::MatrixTransform(matrix, SkSamplingOptions(SkFilterMode::kLinear),
 *                                                  std::move(filter));
 *         filter = SkImageFilters::Offset(v.fX, v.fY, std::move(filter));
 *         return filter;
 *     }
 *
 *     // offset(compose(matrix, offset))
 *     sk_sp<SkImageFilter> makeEarlyComposeFilter(const SkMatrix& matrix) const {
 *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
 *         sk_sp<SkImageFilter> offset = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
 *         sk_sp<SkImageFilter> filter = SkImageFilters::MatrixTransform(
 *                 matrix, SkSamplingOptions(SkFilterMode::kLinear), nullptr);
 *         filter = SkImageFilters::Compose(std::move(filter), std::move(offset));
 *         filter = SkImageFilters::Offset(v.fX, v.fY, std::move(filter));
 *         return filter;
 *     }
 *
 *     // compose(offset, matrix(offset))
 *     sk_sp<SkImageFilter> makeLateComposeFilter(const SkMatrix& matrix) const {
 *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
 *         sk_sp<SkImageFilter> filter = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
 *         filter = SkImageFilters::MatrixTransform(matrix, SkSamplingOptions(SkFilterMode::kLinear),
 *                                                  std::move(filter));
 *         sk_sp<SkImageFilter> offset = SkImageFilters::Offset(v.fX, v.fY, nullptr);
 *         filter = SkImageFilters::Compose(std::move(offset), std::move(filter));
 *         return filter;
 *     }
 *
 *     // compose(offset, compose(matrix, offset))
 *     sk_sp<SkImageFilter> makeFullComposeFilter(const SkMatrix& matrix) const {
 *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
 *         sk_sp<SkImageFilter> offset = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
 *         sk_sp<SkImageFilter> filter = SkImageFilters::MatrixTransform(
 *                 matrix, SkSamplingOptions(SkFilterMode::kLinear), nullptr);
 *         filter = SkImageFilters::Compose(std::move(filter), std::move(offset));
 *         offset = SkImageFilters::Offset(v.fX, v.fY, nullptr);
 *         filter = SkImageFilters::Compose(std::move(offset), std::move(filter));
 *         return filter;
 *     }
 * }
 * ```
 */
public open class ImageFilterComposedTransform public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fDegrees
   * ```
   */
  private var fDegrees: SkScalar = TODO("Initialize fDegrees")

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
   * SkString getName() const override { return SkString("imagefilter_composed_transform"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(512, 512); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAnimate(double nanos) override {
   *         // Animate the rotation angle to test a variety of transformations
   *         fDegrees = TimeUtils::Scaled(1e-9f * nanos, 360.f);
   *         return true;
   *     }
   * ```
   */
  protected override fun onAnimate(nanos: Double): Boolean {
    TODO("Implement onAnimate")
  }

  /**
   * C++ original:
   * ```cpp
   * void onOnceBeforeDraw() override {
   *         fImage = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
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
   *         SkMatrix matrix = SkMatrix::RotateDeg(fDegrees);
   *         // All four quadrants should render the same
   *         this->drawFilter(canvas, 0.f, 0.f, this->makeDirectFilter(matrix));
   *         this->drawFilter(canvas, 256.f, 0.f, this->makeEarlyComposeFilter(matrix));
   *         this->drawFilter(canvas, 0.f, 256.f, this->makeLateComposeFilter(matrix));
   *         this->drawFilter(canvas, 256.f, 256.f, this->makeFullComposeFilter(matrix));
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * void drawFilter(SkCanvas* canvas, SkScalar tx, SkScalar ty, sk_sp<SkImageFilter> filter) const {
   *         SkPaint p;
   *         p.setImageFilter(std::move(filter));
   *
   *         canvas->save();
   *         canvas->translate(tx, ty);
   *         canvas->clipRect(SkRect::MakeWH(256, 256));
   *         canvas->scale(0.5f, 0.5f);
   *         canvas->translate(128, 128);
   *         canvas->drawImage(fImage, 0, 0, SkSamplingOptions(SkFilterMode::kLinear), &p);
   *         canvas->restore();
   *     }
   * ```
   */
  private fun drawFilter(
    canvas: SkCanvas?,
    tx: SkScalar,
    ty: SkScalar,
    filter: SkSp<SkImageFilter>,
  ) {
    TODO("Implement drawFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> makeDirectFilter(const SkMatrix& matrix) const {
   *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
   *         sk_sp<SkImageFilter> filter = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
   *         filter = SkImageFilters::MatrixTransform(matrix, SkSamplingOptions(SkFilterMode::kLinear),
   *                                                  std::move(filter));
   *         filter = SkImageFilters::Offset(v.fX, v.fY, std::move(filter));
   *         return filter;
   *     }
   * ```
   */
  private fun makeDirectFilter(matrix: SkMatrix): SkSp<SkImageFilter> {
    TODO("Implement makeDirectFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> makeEarlyComposeFilter(const SkMatrix& matrix) const {
   *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
   *         sk_sp<SkImageFilter> offset = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
   *         sk_sp<SkImageFilter> filter = SkImageFilters::MatrixTransform(
   *                 matrix, SkSamplingOptions(SkFilterMode::kLinear), nullptr);
   *         filter = SkImageFilters::Compose(std::move(filter), std::move(offset));
   *         filter = SkImageFilters::Offset(v.fX, v.fY, std::move(filter));
   *         return filter;
   *     }
   * ```
   */
  private fun makeEarlyComposeFilter(matrix: SkMatrix): SkSp<SkImageFilter> {
    TODO("Implement makeEarlyComposeFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> makeLateComposeFilter(const SkMatrix& matrix) const {
   *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
   *         sk_sp<SkImageFilter> filter = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
   *         filter = SkImageFilters::MatrixTransform(matrix, SkSamplingOptions(SkFilterMode::kLinear),
   *                                                  std::move(filter));
   *         sk_sp<SkImageFilter> offset = SkImageFilters::Offset(v.fX, v.fY, nullptr);
   *         filter = SkImageFilters::Compose(std::move(offset), std::move(filter));
   *         return filter;
   *     }
   * ```
   */
  private fun makeLateComposeFilter(matrix: SkMatrix): SkSp<SkImageFilter> {
    TODO("Implement makeLateComposeFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> makeFullComposeFilter(const SkMatrix& matrix) const {
   *         SkPoint v = {fImage->width() / 2.f, fImage->height() / 2.f};
   *         sk_sp<SkImageFilter> offset = SkImageFilters::Offset(-v.fX, -v.fY, nullptr);
   *         sk_sp<SkImageFilter> filter = SkImageFilters::MatrixTransform(
   *                 matrix, SkSamplingOptions(SkFilterMode::kLinear), nullptr);
   *         filter = SkImageFilters::Compose(std::move(filter), std::move(offset));
   *         offset = SkImageFilters::Offset(v.fX, v.fY, nullptr);
   *         filter = SkImageFilters::Compose(std::move(offset), std::move(filter));
   *         return filter;
   *     }
   * ```
   */
  private fun makeFullComposeFilter(matrix: SkMatrix): SkSp<SkImageFilter> {
    TODO("Implement makeFullComposeFilter")
  }
}
