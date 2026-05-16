package org.skia.tests

import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ReadPixelsPictureGM : public skiagm::GM {
 * public:
 *     ReadPixelsPictureGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("readpixelspicture"); }
 *
 *     SkISize getISize() override { return SkISize::Make(3 * kWidth, 12 * kHeight); }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (!canvas->imageInfo().colorSpace()) {
 *             *errorMsg = "This gm is only interesting in color correct modes.";
 *             return DrawResult::kSkip;
 *         }
 *
 *         const sk_sp<SkImage> images[] = {
 *                 make_picture_image(),
 *         };
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
 *         const SkImage::CachingHint hints[] = {
 *                 SkImage::kAllow_CachingHint,
 *                 SkImage::kDisallow_CachingHint,
 *         };
 *
 *         for (const sk_sp<SkImage>& image : images) {
 *             for (const sk_sp<SkColorSpace>& dstColorSpace : colorSpaces) {
 *                 canvas->save();
 *                 for (SkColorType dstColorType : colorTypes) {
 *                     for (SkAlphaType dstAlphaType : alphaTypes) {
 *                         for (SkImage::CachingHint hint : hints) {
 *                             draw_image(nullptr, canvas, image.get(), dstColorType, dstAlphaType,
 *                                        dstColorSpace, hint);
 *                             canvas->translate(0.0f, (float) kHeight);
 *                         }
 *                     }
 *                 }
 *                 canvas->restore();
 *                 canvas->translate((float) kWidth, 0.0f);
 *             }
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ReadPixelsPictureGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("readpixelspicture"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(3 * kWidth, 12 * kHeight); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
   *         if (!canvas->imageInfo().colorSpace()) {
   *             *errorMsg = "This gm is only interesting in color correct modes.";
   *             return DrawResult::kSkip;
   *         }
   *
   *         const sk_sp<SkImage> images[] = {
   *                 make_picture_image(),
   *         };
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
   *         const SkImage::CachingHint hints[] = {
   *                 SkImage::kAllow_CachingHint,
   *                 SkImage::kDisallow_CachingHint,
   *         };
   *
   *         for (const sk_sp<SkImage>& image : images) {
   *             for (const sk_sp<SkColorSpace>& dstColorSpace : colorSpaces) {
   *                 canvas->save();
   *                 for (SkColorType dstColorType : colorTypes) {
   *                     for (SkAlphaType dstAlphaType : alphaTypes) {
   *                         for (SkImage::CachingHint hint : hints) {
   *                             draw_image(nullptr, canvas, image.get(), dstColorType, dstAlphaType,
   *                                        dstColorSpace, hint);
   *                             canvas->translate(0.0f, (float) kHeight);
   *                         }
   *                     }
   *                 }
   *                 canvas->restore();
   *                 canvas->translate((float) kWidth, 0.0f);
   *             }
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
