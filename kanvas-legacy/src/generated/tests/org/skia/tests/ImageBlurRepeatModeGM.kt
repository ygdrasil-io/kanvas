package org.skia.tests

import kotlin.Boolean
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ImageBlurRepeatModeGM : public GM {
 * public:
 *     ImageBlurRepeatModeGM() {
 *         this->setBGColor(0xFFCCCCCC);
 *     }
 *
 * protected:
 *     SkString getName() const override { return SkString("imageblurrepeatmode"); }
 *
 *     SkISize getISize() override { return SkISize::Make(850, 920); }
 *
 *     bool runAsBench() const override { return true; }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         sk_sp<SkImage> image[] =
 *                 { make_image(canvas, 1), make_image(canvas, 2), make_image(canvas, 3) };
 *         sk_sp<SkImageFilter> filter;
 *
 *         canvas->translate(0, 30);
 *         // Test different kernel size, including the one to launch 2d Gaussian
 *         // blur.
 *         for (auto sigma: { 0.6f, 3.0f, 8.0f, 20.0f }) {
 *             // FIXME crops
 *             canvas->save();
 *             filter = SkImageFilters::Blur(
 *                     sigma, 0.0f, SkTileMode::kRepeat, nullptr, image[0]->bounds());
 *             draw_image(canvas, image[0], std::move(filter));
 *             canvas->translate(image[0]->width() + 20, 0);
 *
 *             filter = SkImageFilters::Blur(
 *                     0.0f, sigma, SkTileMode::kRepeat, nullptr, image[1]->bounds());
 *             draw_image(canvas, image[1], std::move(filter));
 *             canvas->translate(image[1]->width() + 20, 0);
 *
 *             filter = SkImageFilters::Blur(
 *                     sigma, sigma, SkTileMode::kRepeat, nullptr, image[2]->bounds());
 *             draw_image(canvas, image[2], std::move(filter));
 *             canvas->translate(image[2]->width() + 20, 0);
 *
 *             canvas->restore();
 *             canvas->translate(0, image[0]->height() + 20);
 *         }
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class ImageBlurRepeatModeGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("imageblurrepeatmode"); }
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
   *         sk_sp<SkImage> image[] =
   *                 { make_image(canvas, 1), make_image(canvas, 2), make_image(canvas, 3) };
   *         sk_sp<SkImageFilter> filter;
   *
   *         canvas->translate(0, 30);
   *         // Test different kernel size, including the one to launch 2d Gaussian
   *         // blur.
   *         for (auto sigma: { 0.6f, 3.0f, 8.0f, 20.0f }) {
   *             // FIXME crops
   *             canvas->save();
   *             filter = SkImageFilters::Blur(
   *                     sigma, 0.0f, SkTileMode::kRepeat, nullptr, image[0]->bounds());
   *             draw_image(canvas, image[0], std::move(filter));
   *             canvas->translate(image[0]->width() + 20, 0);
   *
   *             filter = SkImageFilters::Blur(
   *                     0.0f, sigma, SkTileMode::kRepeat, nullptr, image[1]->bounds());
   *             draw_image(canvas, image[1], std::move(filter));
   *             canvas->translate(image[1]->width() + 20, 0);
   *
   *             filter = SkImageFilters::Blur(
   *                     sigma, sigma, SkTileMode::kRepeat, nullptr, image[2]->bounds());
   *             draw_image(canvas, image[2], std::move(filter));
   *             canvas->translate(image[2]->width() + 20, 0);
   *
   *             canvas->restore();
   *             canvas->translate(0, image[0]->height() + 20);
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
