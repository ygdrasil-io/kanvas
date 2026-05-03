package org.skia.tests

import kotlin.String
import org.skia.codec.SkEncodedImageFormat
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EncodeSRGBGM : public GM {
 * public:
 *     EncodeSRGBGM(SkEncodedImageFormat format)
 *         : fEncodedFormat(format)
 *     {}
 *
 * protected:
 *     SkString getName() const override {
 *         const char* format = nullptr;
 *         switch (fEncodedFormat) {
 *             case SkEncodedImageFormat::kPNG:
 *                 format = "png";
 *                 break;
 *             case SkEncodedImageFormat::kWEBP:
 *                 format = "webp";
 *                 break;
 *             case SkEncodedImageFormat::kJPEG:
 *                 format = "jpg";
 *                 break;
 *             default:
 *                 break;
 *         }
 *         return SkStringPrintf("encode-srgb-%s", format);
 *     }
 *
 *     SkISize getISize() override { return SkISize::Make(imageWidth * 2, imageHeight * 15); }
 *
 *     void onDraw(SkCanvas* canvas) override {
 *         const SkColorType colorTypes[] = {
 *             kN32_SkColorType, kRGBA_F16_SkColorType,
 * #if !defined(SK_ENABLE_NDK_IMAGES)
 *             // These fail with the NDK encoders because there is a mismatch between
 *             // Gray_8 and Alpha_8
 *             kGray_8_SkColorType,
 * #endif
 *             kRGB_565_SkColorType,
 *         };
 *         const SkAlphaType alphaTypes[] = {
 *             kUnpremul_SkAlphaType, kPremul_SkAlphaType, kOpaque_SkAlphaType,
 *         };
 *         const sk_sp<SkColorSpace> colorSpaces[] = {
 *             nullptr, SkColorSpace::MakeSRGB(),
 *         };
 *
 *         SkBitmap bitmap;
 *         for (SkColorType colorType : colorTypes) {
 *             for (SkAlphaType alphaType : alphaTypes) {
 *                 canvas->save();
 *                 for (const sk_sp<SkColorSpace>& colorSpace : colorSpaces) {
 *                     make(&bitmap, colorType, alphaType, colorSpace);
 *                     auto data = encode_data(bitmap, fEncodedFormat);
 *                     auto image = SkImages::DeferredFromEncodedData(data);
 *                     canvas->drawImage(image.get(), 0.0f, 0.0f);
 *                     canvas->translate((float) imageWidth, 0.0f);
 *                 }
 *                 canvas->restore();
 *                 canvas->translate(0.0f, (float) imageHeight);
 *             }
 *         }
 *     }
 *
 * private:
 *     SkEncodedImageFormat fEncodedFormat;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class EncodeSRGBGM public constructor(
  format: SkEncodedImageFormat,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * SkEncodedImageFormat fEncodedFormat
   * ```
   */
  private var fEncodedFormat: SkEncodedImageFormat = TODO("Initialize fEncodedFormat")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         const char* format = nullptr;
   *         switch (fEncodedFormat) {
   *             case SkEncodedImageFormat::kPNG:
   *                 format = "png";
   *                 break;
   *             case SkEncodedImageFormat::kWEBP:
   *                 format = "webp";
   *                 break;
   *             case SkEncodedImageFormat::kJPEG:
   *                 format = "jpg";
   *                 break;
   *             default:
   *                 break;
   *         }
   *         return SkStringPrintf("encode-srgb-%s", format);
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override { return SkISize::Make(imageWidth * 2, imageHeight * 15); }
   * ```
   */
  protected override fun getISize(): SkISize {
    TODO("Implement getISize")
  }

  /**
   * C++ original:
   * ```cpp
   * void onDraw(SkCanvas* canvas) override {
   *         const SkColorType colorTypes[] = {
   *             kN32_SkColorType, kRGBA_F16_SkColorType,
   * #if !defined(SK_ENABLE_NDK_IMAGES)
   *             // These fail with the NDK encoders because there is a mismatch between
   *             // Gray_8 and Alpha_8
   *             kGray_8_SkColorType,
   * #endif
   *             kRGB_565_SkColorType,
   *         };
   *         const SkAlphaType alphaTypes[] = {
   *             kUnpremul_SkAlphaType, kPremul_SkAlphaType, kOpaque_SkAlphaType,
   *         };
   *         const sk_sp<SkColorSpace> colorSpaces[] = {
   *             nullptr, SkColorSpace::MakeSRGB(),
   *         };
   *
   *         SkBitmap bitmap;
   *         for (SkColorType colorType : colorTypes) {
   *             for (SkAlphaType alphaType : alphaTypes) {
   *                 canvas->save();
   *                 for (const sk_sp<SkColorSpace>& colorSpace : colorSpaces) {
   *                     make(&bitmap, colorType, alphaType, colorSpace);
   *                     auto data = encode_data(bitmap, fEncodedFormat);
   *                     auto image = SkImages::DeferredFromEncodedData(data);
   *                     canvas->drawImage(image.get(), 0.0f, 0.0f);
   *                     canvas->translate((float) imageWidth, 0.0f);
   *                 }
   *                 canvas->restore();
   *                 canvas->translate(0.0f, (float) imageHeight);
   *             }
   *         }
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?) {
    TODO("Implement onDraw")
  }
}
