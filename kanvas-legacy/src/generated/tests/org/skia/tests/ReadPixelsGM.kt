package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ReadPixelsGM : public skiagm::GM {
 * public:
 *     ReadPixelsGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("readpixels"); }
 *
 *     SkISize getISize() override { return SkISize::Make(6 * kWidth, 9 * kHeight); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkAlphaType alphaTypes[] = {
 *                 kUnpremul_SkAlphaType,
 *                 kPremul_SkAlphaType,
 *         };
 *         const SkColorType colorTypes[] = {
 *                 kRGBA_8888_SkColorType,
 *                 kBGRA_8888_SkColorType,
 *                 kRGBA_F16_SkColorType,
 *         };
 *         const sk_sp<SkColorSpace> colorSpaces[] = {
 *                 make_wide_gamut(),
 *                 SkColorSpace::MakeSRGB(),
 *                 make_small_gamut(),
 *         };
 *
 *         for (const sk_sp<SkColorSpace>& dstColorSpace : colorSpaces) {
 *             for (SkColorType srcColorType : colorTypes) {
 *                 canvas->save();
 *                 sk_sp<SkImage> image = make_raster_image(srcColorType);
 *                 if (!image) {
 *                     continue;
 *                 }
 *                 GrDirectContext* dContext = nullptr;
 * #if defined(SK_GANESH)
 *                 dContext = GrAsDirectContext(canvas->recordingContext());
 *                 if (dContext) {
 *                     image = SkImages::TextureFromImage(dContext, image);
 *                 }
 * #endif
 *                 if (image) {
 *                     for (SkColorType dstColorType : colorTypes) {
 *                         for (SkAlphaType dstAlphaType : alphaTypes) {
 *                             draw_image(dContext, canvas, image.get(), dstColorType, dstAlphaType,
 *                                        dstColorSpace, SkImage::kAllow_CachingHint);
 *                             canvas->translate((float)kWidth, 0.0f);
 *                         }
 *                     }
 *                 }
 *                 canvas->restore();
 *                 canvas->translate(0.0f, (float) kHeight);
 *             }
 *         }
 *     }
 *
 * private:
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ReadPixelsGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("readpixels"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(6 * kWidth, 9 * kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkAlphaType alphaTypes[] = {
   *                 kUnpremul_SkAlphaType,
   *                 kPremul_SkAlphaType,
   *         };
   *         const SkColorType colorTypes[] = {
   *                 kRGBA_8888_SkColorType,
   *                 kBGRA_8888_SkColorType,
   *                 kRGBA_F16_SkColorType,
   *         };
   *         const sk_sp<SkColorSpace> colorSpaces[] = {
   *                 make_wide_gamut(),
   *                 SkColorSpace::MakeSRGB(),
   *                 make_small_gamut(),
   *         };
   *
   *         for (const sk_sp<SkColorSpace>& dstColorSpace : colorSpaces) {
   *             for (SkColorType srcColorType : colorTypes) {
   *                 canvas->save();
   *                 sk_sp<SkImage> image = make_raster_image(srcColorType);
   *                 if (!image) {
   *                     continue;
   *                 }
   *                 GrDirectContext* dContext = nullptr;
   * #if defined(SK_GANESH)
   *                 dContext = GrAsDirectContext(canvas->recordingContext());
   *                 if (dContext) {
   *                     image = SkImages::TextureFromImage(dContext, image);
   *                 }
   * #endif
   *                 if (image) {
   *                     for (SkColorType dstColorType : colorTypes) {
   *                         for (SkAlphaType dstAlphaType : alphaTypes) {
   *                             draw_image(dContext, canvas, image.get(), dstColorType, dstAlphaType,
   *                                        dstColorSpace, SkImage::kAllow_CachingHint);
   *                             canvas->translate((float)kWidth, 0.0f);
   *                         }
   *                     }
   *                 }
   *                 canvas->restore();
   *                 canvas->translate(0.0f, (float) kHeight);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
