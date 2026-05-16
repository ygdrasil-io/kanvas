package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * struct OverdrawColorFilter : public skiagm::GM {
 *     SkString getName() const override { return SkString{"overdrawcolorfilter"}; }
 *
 *     SkISize getISize() override { return {200, 400}; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         static const SkColor colors[SkOverdrawColorFilter::kNumColors] = {
 *                 0x80FF0000, 0x8000FF00, 0x800000FF, 0x80FFFF00, 0x8000FFFF, 0x80FF00FF,
 *         };
 *
 *         SkPaint paint;
 *         paint.setColorFilter(SkOverdrawColorFilter::MakeWithSkColors(colors));
 *         SkSamplingOptions sampling;
 *
 *         SkImageInfo info = SkImageInfo::MakeA8(100, 100);
 *         SkBitmap bitmap;
 *         bitmap.allocPixels(info);
 *         bitmap.eraseARGB(0, 0, 0, 0);
 *         canvas->drawImage(bitmap.asImage(), 0, 0, sampling, &paint);
 *         bitmap.eraseARGB(1, 0, 0, 0);
 *         canvas->drawImage(bitmap.asImage(), 0, 100, sampling, &paint);
 *         bitmap.eraseARGB(2, 0, 0, 0);
 *         canvas->drawImage(bitmap.asImage(), 0, 200, sampling, &paint);
 *         bitmap.eraseARGB(3, 0, 0, 0);
 *         canvas->drawImage(bitmap.asImage(), 0, 300, sampling, &paint);
 *         bitmap.eraseARGB(4, 0, 0, 0);
 *         canvas->drawImage(bitmap.asImage(), 100, 0, sampling, &paint);
 *         bitmap.eraseARGB(5, 0, 0, 0);
 *         canvas->drawImage(bitmap.asImage(), 100, 100, sampling, &paint);
 *         bitmap.eraseARGB(6, 0, 0, 0);
 *         canvas->drawImage(bitmap.asImage(), 100, 200, sampling, &paint);
 *     }
 * }
 * ```
 */
public open class OverdrawColorFilter : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString{"overdrawcolorfilter"}; }
   * ```
   */
  public override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return {200, 400}; }
   * ```
   */
  public override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         static const SkColor colors[SkOverdrawColorFilter::kNumColors] = {
   *                 0x80FF0000, 0x8000FF00, 0x800000FF, 0x80FFFF00, 0x8000FFFF, 0x80FF00FF,
   *         };
   *
   *         SkPaint paint;
   *         paint.setColorFilter(SkOverdrawColorFilter::MakeWithSkColors(colors));
   *         SkSamplingOptions sampling;
   *
   *         SkImageInfo info = SkImageInfo::MakeA8(100, 100);
   *         SkBitmap bitmap;
   *         bitmap.allocPixels(info);
   *         bitmap.eraseARGB(0, 0, 0, 0);
   *         canvas->drawImage(bitmap.asImage(), 0, 0, sampling, &paint);
   *         bitmap.eraseARGB(1, 0, 0, 0);
   *         canvas->drawImage(bitmap.asImage(), 0, 100, sampling, &paint);
   *         bitmap.eraseARGB(2, 0, 0, 0);
   *         canvas->drawImage(bitmap.asImage(), 0, 200, sampling, &paint);
   *         bitmap.eraseARGB(3, 0, 0, 0);
   *         canvas->drawImage(bitmap.asImage(), 0, 300, sampling, &paint);
   *         bitmap.eraseARGB(4, 0, 0, 0);
   *         canvas->drawImage(bitmap.asImage(), 100, 0, sampling, &paint);
   *         bitmap.eraseARGB(5, 0, 0, 0);
   *         canvas->drawImage(bitmap.asImage(), 100, 100, sampling, &paint);
   *         bitmap.eraseARGB(6, 0, 0, 0);
   *         canvas->drawImage(bitmap.asImage(), 100, 200, sampling, &paint);
   *     }
   * ```
   */
  public override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
