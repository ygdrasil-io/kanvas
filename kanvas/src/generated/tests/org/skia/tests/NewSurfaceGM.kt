package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class NewSurfaceGM : public skiagm::GM {
 * public:
 *     NewSurfaceGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("surfacenew"); }
 *
 *     SkISize getISize() override { return SkISize::Make(300, 140); }
 *
 *     static void drawInto(SkCanvas* canvas) {
 *         canvas->drawColor(SK_ColorRED);
 *     }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
 *
 *         auto surf(ToolUtils::makeSurface(canvas, info, nullptr));
 *         drawInto(surf->getCanvas());
 *
 *         sk_sp<SkImage> image(surf->makeImageSnapshot());
 *         canvas->drawImage(image, 10, 10);
 *
 *         auto surf2(surf->makeSurface(info));
 *         drawInto(surf2->getCanvas());
 *
 *         // Assert that the props were communicated transitively through the first image
 *         SkASSERT(equal(surf->props(), surf2->props()));
 *
 *         sk_sp<SkImage> image2(surf2->makeImageSnapshot());
 *         canvas->drawImage(image2.get(), 10 + SkIntToScalar(image->width()) + 10, 10);
 *     }
 *
 * private:
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class NewSurfaceGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("surfacenew"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(300, 140); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         SkImageInfo info = SkImageInfo::MakeN32Premul(100, 100);
   *
   *         auto surf(ToolUtils::makeSurface(canvas, info, nullptr));
   *         drawInto(surf->getCanvas());
   *
   *         sk_sp<SkImage> image(surf->makeImageSnapshot());
   *         canvas->drawImage(image, 10, 10);
   *
   *         auto surf2(surf->makeSurface(info));
   *         drawInto(surf2->getCanvas());
   *
   *         // Assert that the props were communicated transitively through the first image
   *         SkASSERT(equal(surf->props(), surf2->props()));
   *
   *         sk_sp<SkImage> image2(surf2->makeImageSnapshot());
   *         canvas->drawImage(image2.get(), 10 + SkIntToScalar(image->width()) + 10, 10);
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
     * static void drawInto(SkCanvas* canvas) {
     *         canvas->drawColor(SK_ColorRED);
     *     }
     * ```
     */
    protected fun drawInto(canvas: SkCanvas?) {
      TODO("Implement drawInto")
    }
  }
}
