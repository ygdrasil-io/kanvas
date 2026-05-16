package org.skia.tests

import kotlin.Boolean
import kotlin.Double
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkISize
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * class ImageFilterMatrixWLocalMatrix : public skiagm::GM {
 * public:
 *
 *     // Start at 132 degrees, since that resulted in a skipped draw before the fix to
 *     // SkLocalMatrixImageFilter's computeFastBounds() function.
 *     ImageFilterMatrixWLocalMatrix() : fDegrees(132.f) {}
 *
 * protected:
 *     SkString getName() const override { return SkString("imagefilter_matrix_localmatrix"); }
 *
 *     SkISize getISize() override { return SkISize::Make(512, 512); }
 *
 *     bool onAnimate(double nanos) override {
 *         // Animate the rotation angle to ensure the local matrix bounds modifications work
 *         // for a variety of transformations.
 *         fDegrees = TimeUtils::Scaled(1e-9f * nanos, 360.f);
 *         return true;
 *     }
 *
 *     void onOnceBeforeDraw() override {
 *         fImage = ToolUtils::GetResourceAsImage("images/mandrill_256.png");
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkMatrix localMatrix;
 *         localMatrix.preTranslate(128, 128);
 *         localMatrix.preScale(2.0f, 2.0f);
 *
 *         // This matrix applies a rotate around the center of the image (prior to the simulated
 *         // hi-dpi 2x device scale).
 *         SkMatrix filterMatrix;
 *         filterMatrix.setRotate(fDegrees, 64, 64);
 *
 *         sk_sp<SkImageFilter> filter =
 *                 SkImageFilters::MatrixTransform(filterMatrix,
 *                                                 SkSamplingOptions(SkFilterMode::kLinear), nullptr)
 *                              ->makeWithLocalMatrix(localMatrix);
 *
 *         SkPaint p;
 *         p.setImageFilter(filter);
 *         canvas->drawImage(fImage.get(), 128, 128, SkSamplingOptions(), &p);
 *     }
 *
 * private:
 *     SkScalar fDegrees;
 *     sk_sp<SkImage> fImage;
 * }
 * ```
 */
public open class ImageFilterMatrixWLocalMatrix public constructor() : GM() {
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
   * SkString getName() const override { return SkString("imagefilter_matrix_localmatrix"); }
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
   *         // Animate the rotation angle to ensure the local matrix bounds modifications work
   *         // for a variety of transformations.
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
   *         SkMatrix localMatrix;
   *         localMatrix.preTranslate(128, 128);
   *         localMatrix.preScale(2.0f, 2.0f);
   *
   *         // This matrix applies a rotate around the center of the image (prior to the simulated
   *         // hi-dpi 2x device scale).
   *         SkMatrix filterMatrix;
   *         filterMatrix.setRotate(fDegrees, 64, 64);
   *
   *         sk_sp<SkImageFilter> filter =
   *                 SkImageFilters::MatrixTransform(filterMatrix,
   *                                                 SkSamplingOptions(SkFilterMode::kLinear), nullptr)
   *                              ->makeWithLocalMatrix(localMatrix);
   *
   *         SkPaint p;
   *         p.setImageFilter(filter);
   *         canvas->drawImage(fImage.get(), 128, 128, SkSamplingOptions(), &p);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
