package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageBlurClampModeGM : public GM {
 * public:
 *     ImageBlurClampModeGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("imageblurclampmode"); }
 *
 *     SkISize getISize() override { return SkISize::Make(850, 920); }
 *
 *     bool runAsBench() const override { return true; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         sk_sp<SkImage> image(make_image(canvas));
 *         sk_sp<SkImageFilter> filter;
 *
 *         canvas->translate(0, 30);
 *         // Test different kernel size, including the one to launch 2d Gaussian
 *         // blur.
 *         for (auto sigma: { 0.6f, 3.0f, 8.0f, 20.0f }) {
 *             canvas->save();
 *
 *             // x-only blur
 *             filter =  SkImageFilters::Blur(
 *                     sigma, 0.0f, SkTileMode::kClamp, nullptr, image->bounds());
 *             draw_image(canvas, image, std::move(filter));
 *             canvas->translate(image->width() + 20, 0);
 *
 *             // y-only blur
 *             filter = SkImageFilters::Blur(
 *                     0.0f, sigma, SkTileMode::kClamp, nullptr, image->bounds());
 *             draw_image(canvas, image, std::move(filter));
 *             canvas->translate(image->width() + 20, 0);
 *
 *             // both directions
 *             filter = SkImageFilters::Blur(
 *                     sigma, sigma, SkTileMode::kClamp, nullptr, image->bounds());
 *             draw_image(canvas, image, std::move(filter));
 *             canvas->translate(image->width() + 20, 0);
 *
 *             canvas->restore();
 *
 *             canvas->translate(0, image->height() + 20);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageBlurClampModeGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imageblurclampmode"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(850, 920); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * bool runAsBench() const override { return true; }
   * ```
   */
  protected override fun runAsBench(): Boolean {
    TODO("Implement runAsBench")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         sk_sp<SkImage> image(make_image(canvas));
   *         sk_sp<SkImageFilter> filter;
   *
   *         canvas->translate(0, 30);
   *         // Test different kernel size, including the one to launch 2d Gaussian
   *         // blur.
   *         for (auto sigma: { 0.6f, 3.0f, 8.0f, 20.0f }) {
   *             canvas->save();
   *
   *             // x-only blur
   *             filter =  SkImageFilters::Blur(
   *                     sigma, 0.0f, SkTileMode::kClamp, nullptr, image->bounds());
   *             draw_image(canvas, image, std::move(filter));
   *             canvas->translate(image->width() + 20, 0);
   *
   *             // y-only blur
   *             filter = SkImageFilters::Blur(
   *                     0.0f, sigma, SkTileMode::kClamp, nullptr, image->bounds());
   *             draw_image(canvas, image, std::move(filter));
   *             canvas->translate(image->width() + 20, 0);
   *
   *             // both directions
   *             filter = SkImageFilters::Blur(
   *                     sigma, sigma, SkTileMode::kClamp, nullptr, image->bounds());
   *             draw_image(canvas, image, std::move(filter));
   *             canvas->translate(image->width() + 20, 0);
   *
   *             canvas->restore();
   *
   *             canvas->translate(0, image->height() + 20);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
