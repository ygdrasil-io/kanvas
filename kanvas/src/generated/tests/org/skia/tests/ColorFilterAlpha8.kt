package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ColorFilterAlpha8 : public skiagm::GM {
 * public:
 *     ColorFilterAlpha8() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("colorfilteralpha8"); }
 *
 *     SkISize getISize() override { return SkISize::Make(400, 400); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         canvas->clear(SK_ColorRED);
 *
 *         SkBitmap bitmap;
 *         SkImageInfo info = SkImageInfo::MakeA8(200, 200);
 *         bitmap.allocPixels(info);
 *         bitmap.eraseColor(0x88FFFFFF);
 *
 *         SkPaint paint;
 *         float opaqueGrayMatrix[20] = {
 *                 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
 *                 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
 *                 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
 *                 0.0f, 0.0f, 0.0f, 0.0f, 1.0f
 *         };
 *         paint.setColorFilter(SkColorFilters::Matrix(opaqueGrayMatrix));
 *
 *         canvas->drawImage(bitmap.asImage(), 100.0f, 100.0f, SkSamplingOptions(), &paint);
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ColorFilterAlpha8 public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("colorfilteralpha8"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(400, 400); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         canvas->clear(SK_ColorRED);
   *
   *         SkBitmap bitmap;
   *         SkImageInfo info = SkImageInfo::MakeA8(200, 200);
   *         bitmap.allocPixels(info);
   *         bitmap.eraseColor(0x88FFFFFF);
   *
   *         SkPaint paint;
   *         float opaqueGrayMatrix[20] = {
   *                 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
   *                 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
   *                 0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
   *                 0.0f, 0.0f, 0.0f, 0.0f, 1.0f
   *         };
   *         paint.setColorFilter(SkColorFilters::Matrix(opaqueGrayMatrix));
   *
   *         canvas->drawImage(bitmap.asImage(), 100.0f, 100.0f, SkSamplingOptions(), &paint);
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
