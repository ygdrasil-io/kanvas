package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class ReadPixelsCodecGM : public skiagm::GM {
 * public:
 *     ReadPixelsCodecGM() {}
 *
 * protected:
 *     SkString getName() const override { return SkString("readpixelscodec"); }
 *
 *     SkISize getISize() override {
 *         return SkISize::Make(3 * (kEncodedWidth + 1), 12 * (kEncodedHeight + 1));
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         if (!canvas->imageInfo().colorSpace()) {
 *             *errorMsg = "This gm is only interesting in color correct modes.";
 *             return DrawResult::kSkip;
 *         }
 *
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
 *         sk_sp<SkImage> image = make_codec_image();
 *         for (const sk_sp<SkColorSpace>& dstColorSpace : colorSpaces) {
 *             canvas->save();
 *             for (SkColorType dstColorType : colorTypes) {
 *                 for (SkAlphaType dstAlphaType : alphaTypes) {
 *                     for (SkImage::CachingHint hint : hints) {
 *                         draw_image(nullptr, canvas, image.get(), dstColorType, dstAlphaType,
 *                                    dstColorSpace, hint);
 *                         canvas->translate(0.0f, (float) kEncodedHeight + 1);
 *                     }
 *                 }
 *             }
 *             canvas->restore();
 *             canvas->translate((float) kEncodedWidth + 1, 0.0f);
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     static const int kEncodedWidth = 8;
 *     static const int kEncodedHeight = 8;
 *
 *     using INHERITED = skiagm::GM;
 * }
 * ```
 */
public open class ReadPixelsCodecGM public constructor() : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override { return SkString("readpixelscodec"); }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         return SkISize::Make(3 * (kEncodedWidth + 1), 12 * (kEncodedHeight + 1));
   *     }
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
   *         sk_sp<SkImage> image = make_codec_image();
   *         for (const sk_sp<SkColorSpace>& dstColorSpace : colorSpaces) {
   *             canvas->save();
   *             for (SkColorType dstColorType : colorTypes) {
   *                 for (SkAlphaType dstAlphaType : alphaTypes) {
   *                     for (SkImage::CachingHint hint : hints) {
   *                         draw_image(nullptr, canvas, image.get(), dstColorType, dstAlphaType,
   *                                    dstColorSpace, hint);
   *                         canvas->translate(0.0f, (float) kEncodedHeight + 1);
   *                     }
   *                 }
   *             }
   *             canvas->restore();
   *             canvas->translate((float) kEncodedWidth + 1, 0.0f);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }

  public companion object {
    private val kEncodedWidth: Int = TODO("Initialize kEncodedWidth")

    private val kEncodedHeight: Int = TODO("Initialize kEncodedHeight")
  }
}
