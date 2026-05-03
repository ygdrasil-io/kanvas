package org.skia.tests

import kotlin.Int
import kotlin.String
import org.skia.codec.SkEncodedImageFormat
import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * C++ original:
 * ```cpp
 * class EncodeColorTypesGM : public GM {
 * public:
 *     EncodeColorTypesGM(SkEncodedImageFormat format, int quality, Variant variant, const char* name)
 *         : fFormat(format)
 *         , fQuality(quality)
 *         , fVariant(variant)
 *         , fName(name)
 *     {}
 *
 * protected:
 *     SkString getName() const override {
 *         const char* variant = fVariant == Variant::kOpaque ? "opaque-":
 *                               fVariant == Variant::kGray   ? "gray-"  :
 *                                                              ""       ;
 *         return SkStringPrintf("encode-%scolor-types-%s", variant, fName);
 *     }
 *
 *     SkISize getISize() override {
 *         const int width = fVariant == Variant::kNormal ? imageWidth * 7 : imageWidth * 2;
 *         return SkISize::Make(width, imageHeight);
 *     }
 *
 *     DrawResult onDraw(SkCanvas* canvas, SkString* errorMsg) override {
 *         const auto colorType = canvas->imageInfo().colorType();
 *         switch (fVariant) {
 *             case Variant::kGray:
 *                 if (colorType != kGray_8_SkColorType) {
 *                     return DrawResult::kSkip;
 *                 }
 *                 break;
 *             case Variant::kOpaque:
 *                 if (colorType != kRGB_565_SkColorType         &&
 *                     colorType != kRGB_888x_SkColorType        &&
 *                     colorType != kRGB_101010x_SkColorType     &&
 *                     colorType != kRGB_F16F16F16x_SkColorType  &&
 *                     colorType != kBGR_101010x_SkColorType)
 *                 {
 *                     return DrawResult::kSkip;
 *                 }
 *                 break;
 *             case Variant::kNormal:
 *                 if (colorType != kARGB_4444_SkColorType    &&
 *                     colorType != kRGBA_8888_SkColorType    &&
 *                     colorType != kBGRA_8888_SkColorType    &&
 *                     colorType != kRGBA_1010102_SkColorType &&
 *                     colorType != kBGRA_1010102_SkColorType &&
 *                     colorType != kRGBA_F16Norm_SkColorType &&
 *                     colorType != kRGBA_F16_SkColorType     &&
 *                     colorType != kRGBA_F32_SkColorType)
 *                 {
 *                     return DrawResult::kSkip;
 *                 }
 *             break;
 *         }
 *         const SkAlphaType alphaTypes[] = {
 *             kOpaque_SkAlphaType, kPremul_SkAlphaType, kUnpremul_SkAlphaType,
 *         };
 *
 *         for (SkAlphaType alphaType : alphaTypes) {
 *             auto src = make_image(colorType, alphaType);
 *             if (!src) {
 *                 break;
 *             }
 *             SkASSERT_RELEASE(fFormat == SkEncodedImageFormat::kWEBP);
 *             SkWebpEncoder::Options options;
 *             if (fQuality < 100) {
 *                 options.fCompression = SkWebpEncoder::Compression::kLossy;
 *                 options.fQuality = fQuality;
 *             } else {
 *                 options.fCompression = SkWebpEncoder::Compression::kLossless;
 *                 // in lossless mode, this is effort. 70 is the default effort in SkImageEncoder,
 *                 // which follows Blink and WebPConfigInit.
 *                 options.fQuality = 70;
 *             }
 *             auto data = SkWebpEncoder::Encode(nullptr, src.get(), options);
 *             SkASSERT(data);
 *             auto decoded = SkImages::DeferredFromEncodedData(data);
 *             if (!decoded) {
 *                 break;
 *             }
 *
 *             canvas->drawImage(src, 0.0f, 0.0f);
 *             canvas->translate((float) imageWidth, 0.0f);
 *
 *             canvas->drawImage(decoded, 0.0f, 0.0f);
 *             canvas->translate((float) imageWidth * 1.5, 0.0f);
 *         }
 *         return DrawResult::kOk;
 *     }
 *
 * private:
 *     const SkEncodedImageFormat fFormat;
 *     const int                  fQuality;
 *     const Variant              fVariant;
 *     const char*                fName;
 *
 *     using INHERITED = GM;
 * }
 * ```
 */
public open class EncodeColorTypesGM public constructor(
  format: SkEncodedImageFormat,
  quality: Int,
  variant: Variant,
  name: String?,
) : GM() {
  /**
   * C++ original:
   * ```cpp
   * const SkEncodedImageFormat fFormat
   * ```
   */
  private val fFormat: SkEncodedImageFormat = TODO("Initialize fFormat")

  /**
   * C++ original:
   * ```cpp
   * const int                  fQuality
   * ```
   */
  private val fQuality: Int = TODO("Initialize fQuality")

  /**
   * C++ original:
   * ```cpp
   * const Variant              fVariant
   * ```
   */
  private val fVariant: Variant = TODO("Initialize fVariant")

  /**
   * C++ original:
   * ```cpp
   * const char*                fName
   * ```
   */
  private val fName: String? = TODO("Initialize fName")

  /**
   * C++ original:
   * ```cpp
   * SkString getName() const override {
   *         const char* variant = fVariant == Variant::kOpaque ? "opaque-":
   *                               fVariant == Variant::kGray   ? "gray-"  :
   *                                                              ""       ;
   *         return SkStringPrintf("encode-%scolor-types-%s", variant, fName);
   *     }
   * ```
   */
  protected override fun getName(): String {
    TODO("Implement getName")
  }

  /**
   * C++ original:
   * ```cpp
   * SkISize getISize() override {
   *         const int width = fVariant == Variant::kNormal ? imageWidth * 7 : imageWidth * 2;
   *         return SkISize::Make(width, imageHeight);
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
   *         const auto colorType = canvas->imageInfo().colorType();
   *         switch (fVariant) {
   *             case Variant::kGray:
   *                 if (colorType != kGray_8_SkColorType) {
   *                     return DrawResult::kSkip;
   *                 }
   *                 break;
   *             case Variant::kOpaque:
   *                 if (colorType != kRGB_565_SkColorType         &&
   *                     colorType != kRGB_888x_SkColorType        &&
   *                     colorType != kRGB_101010x_SkColorType     &&
   *                     colorType != kRGB_F16F16F16x_SkColorType  &&
   *                     colorType != kBGR_101010x_SkColorType)
   *                 {
   *                     return DrawResult::kSkip;
   *                 }
   *                 break;
   *             case Variant::kNormal:
   *                 if (colorType != kARGB_4444_SkColorType    &&
   *                     colorType != kRGBA_8888_SkColorType    &&
   *                     colorType != kBGRA_8888_SkColorType    &&
   *                     colorType != kRGBA_1010102_SkColorType &&
   *                     colorType != kBGRA_1010102_SkColorType &&
   *                     colorType != kRGBA_F16Norm_SkColorType &&
   *                     colorType != kRGBA_F16_SkColorType     &&
   *                     colorType != kRGBA_F32_SkColorType)
   *                 {
   *                     return DrawResult::kSkip;
   *                 }
   *             break;
   *         }
   *         const SkAlphaType alphaTypes[] = {
   *             kOpaque_SkAlphaType, kPremul_SkAlphaType, kUnpremul_SkAlphaType,
   *         };
   *
   *         for (SkAlphaType alphaType : alphaTypes) {
   *             auto src = make_image(colorType, alphaType);
   *             if (!src) {
   *                 break;
   *             }
   *             SkASSERT_RELEASE(fFormat == SkEncodedImageFormat::kWEBP);
   *             SkWebpEncoder::Options options;
   *             if (fQuality < 100) {
   *                 options.fCompression = SkWebpEncoder::Compression::kLossy;
   *                 options.fQuality = fQuality;
   *             } else {
   *                 options.fCompression = SkWebpEncoder::Compression::kLossless;
   *                 // in lossless mode, this is effort. 70 is the default effort in SkImageEncoder,
   *                 // which follows Blink and WebPConfigInit.
   *                 options.fQuality = 70;
   *             }
   *             auto data = SkWebpEncoder::Encode(nullptr, src.get(), options);
   *             SkASSERT(data);
   *             auto decoded = SkImages::DeferredFromEncodedData(data);
   *             if (!decoded) {
   *                 break;
   *             }
   *
   *             canvas->drawImage(src, 0.0f, 0.0f);
   *             canvas->translate((float) imageWidth, 0.0f);
   *
   *             canvas->drawImage(decoded, 0.0f, 0.0f);
   *             canvas->translate((float) imageWidth * 1.5, 0.0f);
   *         }
   *         return DrawResult::kOk;
   *     }
   * ```
   */
  protected override fun onDraw(canvas: SkCanvas?, errorMsg: String?): DrawResult {
    TODO("Implement onDraw")
  }
}
