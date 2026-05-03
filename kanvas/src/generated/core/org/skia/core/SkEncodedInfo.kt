package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.UByte
import org.skia.codec.ColorProfile

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkEncodedInfo {
 * public:
 *     enum Alpha {
 *         kOpaque_Alpha,
 *         kUnpremul_Alpha,
 *
 *         // Each pixel is either fully opaque or fully transparent.
 *         // There is no difference between requesting kPremul or kUnpremul.
 *         kBinary_Alpha,
 *     };
 *
 *     /*
 *      * We strive to make the number of components per pixel obvious through
 *      * our naming conventions.
 *      * Ex: kRGB has 3 components.  kRGBA has 4 components.
 *      *
 *      * This sometimes results in redundant Alpha and Color information.
 *      * Ex: kRGB images must also be kOpaque.
 *      */
 *     enum Color {
 *         // PNG, WBMP
 *         kGray_Color,
 *
 *         // PNG
 *         kGrayAlpha_Color,
 *
 *         // PNG with Skia-specific sBIT
 *         // Like kGrayAlpha, except this expects to be treated as
 *         // kAlpha_8_SkColorType, which ignores the gray component. If
 *         // decoded to full color (e.g. kN32), the gray component is respected
 *         // (so it can share code with kGrayAlpha).
 *         kXAlpha_Color,
 *
 *         // PNG
 *         // 565 images may be encoded to PNG by specifying the number of
 *         // significant bits for each channel.  This is a strange 565
 *         // representation because the image is still encoded with 8 bits per
 *         // component.
 *         k565_Color,
 *
 *         // PNG, GIF, BMP
 *         kPalette_Color,
 *
 *         // PNG, RAW
 *         kRGB_Color,
 *         kRGBA_Color,
 *
 *         // BMP
 *         kBGR_Color,
 *         kBGRX_Color,
 *         kBGRA_Color,
 *
 *         // JPEG, WEBP
 *         kYUV_Color,
 *
 *         // WEBP
 *         kYUVA_Color,
 *
 *         // JPEG
 *         // Photoshop actually writes inverted CMYK data into JPEGs, where zero
 *         // represents 100% ink coverage.  For this reason, we treat CMYK JPEGs
 *         // as having inverted CMYK.  libjpeg-turbo warns that this may break
 *         // other applications, but the CMYK JPEGs we see on the web expect to
 *         // be treated as inverted CMYK.
 *         kInvertedCMYK_Color,
 *         kYCCK_Color,
 *     };
 *
 *     static SkEncodedInfo Make(
 *         int width, int height, Color color, Alpha alpha, int bitsPerComponent);
 *
 *     static SkEncodedInfo Make(
 *         int width, int height, Color color, Alpha alpha, int bitsPerComponent,
 *         std::unique_ptr<SkCodecs::ColorProfile> profile);
 *
 *     static SkEncodedInfo Make(
 *         int width, int height, Color color, Alpha alpha, int bitsPerComponent,
 *         std::unique_ptr<SkCodecs::ColorProfile> profile, int colorDepth);
 *
 *     static SkEncodedInfo Make(
 *         int width, int height, Color color, Alpha alpha, int bitsPerComponent,
 *         int colorDepth,
 *         std::unique_ptr<SkCodecs::ColorProfile> profile, const skhdr::Metadata& hdrMetadata);
 *
 *     /*
 *      * Returns a recommended SkImageInfo.
 *      *
 *      * TODO: Leave this up to the client.
 *      */
 *     SkImageInfo makeImageInfo() const;
 *
 *     int   width() const { return fWidth;  }
 *     int  height() const { return fHeight; }
 *     Color color() const { return fColor;  }
 *     Alpha alpha() const { return fAlpha;  }
 *     bool opaque() const { return fAlpha == kOpaque_Alpha; }
 *
 *     // TODO(https://issues.skia.org/issues/464217864): Remove direct access to the
 *     // skcms_ICCProfile and change profileData() to serialize a new profile.
 *     const skcms_ICCProfile* profile() const;
 *     sk_sp<const SkData> profileData() const;
 *     const SkCodecs::ColorProfile* colorProfile() const {
 *         return fColorProfile.get();
 *     }
 *
 *     uint8_t bitsPerComponent() const { return fBitsPerComponent; }
 *
 *     uint8_t bitsPerPixel() const {
 *         switch (fColor) {
 *             case kGray_Color:
 *                 return fBitsPerComponent;
 *             case kXAlpha_Color:
 *             case kGrayAlpha_Color:
 *                 return 2 * fBitsPerComponent;
 *             case kPalette_Color:
 *                 return fBitsPerComponent;
 *             case kRGB_Color:
 *             case kBGR_Color:
 *             case kYUV_Color:
 *             case k565_Color:
 *                 return 3 * fBitsPerComponent;
 *             case kRGBA_Color:
 *             case kBGRA_Color:
 *             case kBGRX_Color:
 *             case kYUVA_Color:
 *             case kInvertedCMYK_Color:
 *             case kYCCK_Color:
 *                 return 4 * fBitsPerComponent;
 *         }
 *         SkASSERT(false);
 *         return 0;
 *     }
 *
 *     SkEncodedInfo(const SkEncodedInfo& orig) = delete;
 *     SkEncodedInfo& operator=(const SkEncodedInfo&) = delete;
 *
 *     SkEncodedInfo(SkEncodedInfo&& orig);
 *     SkEncodedInfo& operator=(SkEncodedInfo&&);
 *
 *     // Explicit copy method, to avoid accidental copying.
 *     SkEncodedInfo copy() const;
 *
 *     // Return number of bits of R/G/B channel
 *     uint8_t getColorDepth() const {
 *         return fColorDepth;
 *     }
 *
 *     // Return the HDR metadata associated with this image. Note that even SDR images can include
 *     // HDR metadata (e.g, indicating how to inverse tone map when displayed on an HDR display).
 *     const skhdr::Metadata& getHdrMetadata() const {
 *         return fHdrMetadata;
 *     }
 *
 *     ~SkEncodedInfo();
 *
 * private:
 *     SkEncodedInfo(
 *         int width, int height, Color color, Alpha alpha, uint8_t bitsPerComponent,
 *         uint8_t colorDepth,
 *         std::unique_ptr<SkCodecs::ColorProfile> profile, const skhdr::Metadata& hdrMetadata);
 *
 *     static void VerifyColor(Color color, Alpha alpha, int bitsPerComponent) {
 *         // Avoid `-Wunused-parameter` warnings on non-debug builds.
 *         std::ignore = alpha;
 *         std::ignore = bitsPerComponent;
 *
 *         switch (color) {
 *             case kGray_Color:
 *                 SkASSERT(kOpaque_Alpha == alpha);
 *                 return;
 *             case kGrayAlpha_Color:
 *                 SkASSERT(kOpaque_Alpha != alpha);
 *                 return;
 *             case kPalette_Color:
 *                 SkASSERT(16 != bitsPerComponent);
 *                 return;
 *             case kRGB_Color:
 *             case kBGR_Color:
 *             case kBGRX_Color:
 *                 SkASSERT(kOpaque_Alpha == alpha);
 *                 SkASSERT(bitsPerComponent >= 8);
 *                 return;
 *             case kYUV_Color:
 *             case kInvertedCMYK_Color:
 *             case kYCCK_Color:
 *                 SkASSERT(kOpaque_Alpha == alpha);
 *                 SkASSERT(8 == bitsPerComponent);
 *                 return;
 *             case kRGBA_Color:
 *                 SkASSERT(bitsPerComponent >= 8);
 *                 return;
 *             case kBGRA_Color:
 *             case kYUVA_Color:
 *                 SkASSERT(8 == bitsPerComponent);
 *                 return;
 *             case kXAlpha_Color:
 *                 SkASSERT(kUnpremul_Alpha == alpha);
 *                 SkASSERT(8 == bitsPerComponent);
 *                 return;
 *             case k565_Color:
 *                 SkASSERT(kOpaque_Alpha == alpha);
 *                 SkASSERT(8 == bitsPerComponent);
 *                 return;
 *         }
 *         SkASSERT(false);  // Unrecognized `color` enum value.
 *     }
 *
 *     int     fWidth;
 *     int     fHeight;
 *     Color   fColor;
 *     Alpha   fAlpha;
 *     uint8_t fBitsPerComponent;
 *     uint8_t fColorDepth;
 *     std::unique_ptr<const SkCodecs::ColorProfile> fColorProfile;
 *     skhdr::Metadata                               fHdrMetadata;
 * }
 * ```
 */
public data class SkEncodedInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * int     fWidth
   * ```
   */
  private var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * int     fHeight
   * ```
   */
  private var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * Color   fColor
   * ```
   */
  private var fColor: Color,
  /**
   * C++ original:
   * ```cpp
   * Alpha   fAlpha
   * ```
   */
  private var fAlpha: Alpha,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fBitsPerComponent
   * ```
   */
  private var fBitsPerComponent: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t fColorDepth
   * ```
   */
  private var fColorDepth: UByte,
  /**
   * C++ original:
   * ```cpp
   * std::unique_ptr<const SkCodecs::ColorProfile> fColorProfile
   * ```
   */
  private val fColorProfile: ColorProfile?,
  /**
   * C++ original:
   * ```cpp
   * skhdr::Metadata                               fHdrMetadata
   * ```
   */
  private var fHdrMetadata: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkImageInfo SkEncodedInfo::makeImageInfo() const {
   *     auto ct =  kGray_Color == fColor ? kGray_8_SkColorType   :
   *              kXAlpha_Color == fColor ? kAlpha_8_SkColorType  :
   *                 k565_Color == fColor ? kRGB_565_SkColorType  :
   *                                        kN32_SkColorType      ;
   *     auto alpha = kOpaque_Alpha == fAlpha ? kOpaque_SkAlphaType
   *                                          : kUnpremul_SkAlphaType;
   *     auto cs = fColorProfile ? fColorProfile->getExactColorSpace() : SkColorSpace::MakeSRGB();
   *     if (!cs) {
   *         cs = SkColorSpace::MakeSRGB();
   *     }
   *     return SkImageInfo::Make(fWidth, fHeight, ct, alpha, std::move(cs));
   * }
   * ```
   */
  public fun makeImageInfo(): Int {
    TODO("Implement makeImageInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * int   width() const { return fWidth;  }
   * ```
   */
  public fun width(): Int {
    TODO("Implement width")
  }

  /**
   * C++ original:
   * ```cpp
   * int  height() const { return fHeight; }
   * ```
   */
  public fun height(): Int {
    TODO("Implement height")
  }

  /**
   * C++ original:
   * ```cpp
   * Color color() const { return fColor;  }
   * ```
   */
  public fun color(): Color {
    TODO("Implement color")
  }

  /**
   * C++ original:
   * ```cpp
   * Alpha alpha() const { return fAlpha;  }
   * ```
   */
  public fun alpha(): Alpha {
    TODO("Implement alpha")
  }

  /**
   * C++ original:
   * ```cpp
   * bool opaque() const { return fAlpha == kOpaque_Alpha; }
   * ```
   */
  public fun opaque(): Boolean {
    TODO("Implement opaque")
  }

  /**
   * C++ original:
   * ```cpp
   * const skcms_ICCProfile* SkEncodedInfo::profile() const {
   *     if (!fColorProfile) return nullptr;
   *     return fColorProfile->profile();
   * }
   * ```
   */
  public fun profile(): Int {
    TODO("Implement profile")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<const SkData> SkEncodedInfo::profileData() const {
   *     if (!fColorProfile) return nullptr;
   *     return fColorProfile->data();
   * }
   * ```
   */
  public fun profileData(): Int {
    TODO("Implement profileData")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkCodecs::ColorProfile* colorProfile() const {
   *         return fColorProfile.get();
   *     }
   * ```
   */
  public fun colorProfile(): ColorProfile {
    TODO("Implement colorProfile")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t bitsPerComponent() const { return fBitsPerComponent; }
   * ```
   */
  public fun bitsPerComponent(): UByte {
    TODO("Implement bitsPerComponent")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t bitsPerPixel() const {
   *         switch (fColor) {
   *             case kGray_Color:
   *                 return fBitsPerComponent;
   *             case kXAlpha_Color:
   *             case kGrayAlpha_Color:
   *                 return 2 * fBitsPerComponent;
   *             case kPalette_Color:
   *                 return fBitsPerComponent;
   *             case kRGB_Color:
   *             case kBGR_Color:
   *             case kYUV_Color:
   *             case k565_Color:
   *                 return 3 * fBitsPerComponent;
   *             case kRGBA_Color:
   *             case kBGRA_Color:
   *             case kBGRX_Color:
   *             case kYUVA_Color:
   *             case kInvertedCMYK_Color:
   *             case kYCCK_Color:
   *                 return 4 * fBitsPerComponent;
   *         }
   *         SkASSERT(false);
   *         return 0;
   *     }
   * ```
   */
  public fun bitsPerPixel(): UByte {
    TODO("Implement bitsPerPixel")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEncodedInfo& operator=(const SkEncodedInfo&) = delete
   * ```
   */
  public fun assign(param0: SkEncodedInfo) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEncodedInfo& SkEncodedInfo::operator=(SkEncodedInfo&&)
   * ```
   */
  public fun copy(): SkEncodedInfo {
    TODO("Implement copy")
  }

  /**
   * C++ original:
   * ```cpp
   * SkEncodedInfo SkEncodedInfo::copy() const {
   *     return SkEncodedInfo(
   *         fWidth, fHeight, fColor, fAlpha, fBitsPerComponent, fColorDepth,
   *         fColorProfile ? fColorProfile->clone() : nullptr, fHdrMetadata);
   * }
   * ```
   */
  public fun getColorDepth(): UByte {
    TODO("Implement getColorDepth")
  }

  /**
   * C++ original:
   * ```cpp
   * uint8_t getColorDepth() const {
   *         return fColorDepth;
   *     }
   * ```
   */
  public fun getHdrMetadata(): Int {
    TODO("Implement getHdrMetadata")
  }

  public enum class Alpha {
    kOpaque_Alpha,
    kUnpremul_Alpha,
    kBinary_Alpha,
  }

  public enum class Color {
    kGray_Color,
    kGrayAlpha_Color,
    kXAlpha_Color,
    k565_Color,
    kPalette_Color,
    kRGB_Color,
    kRGBA_Color,
    kBGR_Color,
    kBGRX_Color,
    kBGRA_Color,
    kYUV_Color,
    kYUVA_Color,
    kInvertedCMYK_Color,
    kYCCK_Color,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkEncodedInfo SkEncodedInfo::Make(
     *         int width, int height, Color color, Alpha alpha, int bitsPerComponent) {
     *     return Make(width, height, color, alpha, bitsPerComponent, nullptr);
     * }
     * ```
     */
    public fun make(
      width: Int,
      height: Int,
      color: Color,
      alpha: Alpha,
      bitsPerComponent: Int,
    ): SkEncodedInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkEncodedInfo SkEncodedInfo::Make(
     *         int width, int height, Color color, Alpha alpha, int bitsPerComponent,
     *         std::unique_ptr<SkCodecs::ColorProfile> profile) {
     *     return Make(width, height, color, alpha, /*bitsPerComponent*/ bitsPerComponent,
     *             std::move(profile), /*colorDepth*/ bitsPerComponent);
     * }
     * ```
     */
    public fun make(
      width: Int,
      height: Int,
      color: Color,
      alpha: Alpha,
      bitsPerComponent: Int,
      profile: ColorProfile?,
    ): SkEncodedInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkEncodedInfo SkEncodedInfo::Make(
     *         int width, int height, Color color, Alpha alpha, int bitsPerComponent,
     *         std::unique_ptr<SkCodecs::ColorProfile> profile, int colorDepth) {
     *     return Make(width, height, color, alpha, bitsPerComponent, colorDepth, std::move(profile),
     *                 skhdr::Metadata::MakeEmpty());
     * }
     * ```
     */
    public fun make(
      width: Int,
      height: Int,
      color: Color,
      alpha: Alpha,
      bitsPerComponent: Int,
      profile: ColorProfile?,
      colorDepth: Int,
    ): SkEncodedInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkEncodedInfo SkEncodedInfo::Make(
     *         int width, int height, Color color, Alpha alpha, int bitsPerComponent, int colorDepth,
     *         std::unique_ptr<SkCodecs::ColorProfile> profile, const skhdr::Metadata& hdrMetadata) {
     *     SkASSERT(1 == bitsPerComponent ||
     *              2 == bitsPerComponent ||
     *              4 == bitsPerComponent ||
     *              8 == bitsPerComponent ||
     *              16 == bitsPerComponent);
     *     VerifyColor(color, alpha, bitsPerComponent);
     *     return SkEncodedInfo(width, height, color, alpha, SkToU8(bitsPerComponent), SkToU8(colorDepth),
     *                          std::move(profile), hdrMetadata);
     * }
     * ```
     */
    public fun make(
      width: Int,
      height: Int,
      color: Color,
      alpha: Alpha,
      bitsPerComponent: Int,
      colorDepth: Int,
      profile: ColorProfile?,
      hdrMetadata: Metadata,
    ): SkEncodedInfo {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * static void VerifyColor(Color color, Alpha alpha, int bitsPerComponent) {
     *         // Avoid `-Wunused-parameter` warnings on non-debug builds.
     *         std::ignore = alpha;
     *         std::ignore = bitsPerComponent;
     *
     *         switch (color) {
     *             case kGray_Color:
     *                 SkASSERT(kOpaque_Alpha == alpha);
     *                 return;
     *             case kGrayAlpha_Color:
     *                 SkASSERT(kOpaque_Alpha != alpha);
     *                 return;
     *             case kPalette_Color:
     *                 SkASSERT(16 != bitsPerComponent);
     *                 return;
     *             case kRGB_Color:
     *             case kBGR_Color:
     *             case kBGRX_Color:
     *                 SkASSERT(kOpaque_Alpha == alpha);
     *                 SkASSERT(bitsPerComponent >= 8);
     *                 return;
     *             case kYUV_Color:
     *             case kInvertedCMYK_Color:
     *             case kYCCK_Color:
     *                 SkASSERT(kOpaque_Alpha == alpha);
     *                 SkASSERT(8 == bitsPerComponent);
     *                 return;
     *             case kRGBA_Color:
     *                 SkASSERT(bitsPerComponent >= 8);
     *                 return;
     *             case kBGRA_Color:
     *             case kYUVA_Color:
     *                 SkASSERT(8 == bitsPerComponent);
     *                 return;
     *             case kXAlpha_Color:
     *                 SkASSERT(kUnpremul_Alpha == alpha);
     *                 SkASSERT(8 == bitsPerComponent);
     *                 return;
     *             case k565_Color:
     *                 SkASSERT(kOpaque_Alpha == alpha);
     *                 SkASSERT(8 == bitsPerComponent);
     *                 return;
     *         }
     *         SkASSERT(false);  // Unrecognized `color` enum value.
     *     }
     * ```
     */
    private fun verifyColor(
      color: Color,
      alpha: Alpha,
      bitsPerComponent: Int,
    ) {
      TODO("Implement verifyColor")
    }
  }
}
