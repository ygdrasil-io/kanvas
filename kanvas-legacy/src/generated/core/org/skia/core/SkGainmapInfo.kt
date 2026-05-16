package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkData

/**
 * C++ original:
 * ```cpp
 * struct SkGainmapInfo {
 *     /**
 *      *  Parameters for converting the gainmap from its image encoding to log space. These are
 *      *  specified per color channel. The alpha value is unused.
 *      */
 *     SkColor4f fGainmapRatioMin = {1.f, 1.f, 1.f, 1.0};
 *     SkColor4f fGainmapRatioMax = {2.f, 2.f, 2.f, 1.0};
 *     SkColor4f fGainmapGamma = {1.f, 1.f, 1.f, 1.f};
 *
 *     /**
 *      *  Parameters sometimes used in gainmap computation to avoid numerical instability.
 *      */
 *     SkColor4f fEpsilonSdr = {0.f, 0.f, 0.f, 1.0};
 *     SkColor4f fEpsilonHdr = {0.f, 0.f, 0.f, 1.0};
 *
 *     /**
 *      *  If the output display's HDR to SDR ratio is less or equal than fDisplayRatioSdr then the SDR
 *      *  rendition is displayed. If the output display's HDR to SDR ratio is greater or equal than
 *      *  fDisplayRatioHdr then the HDR rendition is displayed. If the output display's HDR to SDR
 *      *  ratio is between these values then an interpolation between the two is displayed using the
 *      *  math above.
 *      */
 *     float fDisplayRatioSdr = 1.f;
 *     float fDisplayRatioHdr = 2.f;
 *
 *     /**
 *      *  Whether the base image is the SDR image or the HDR image.
 *      */
 *     enum class BaseImageType {
 *         kSDR,
 *         kHDR,
 *     };
 *     BaseImageType fBaseImageType = BaseImageType::kSDR;
 *
 *     /**
 *      *  The type of the gainmap image. If the type is kApple, then the gainmap image was originally
 *      *  encoded according to the specification at [0], and can be converted to the kDefault type by
 *      *  applying the transformation described at [1].
 *      *  [0] https://developer.apple.com/documentation/appkit/images_and_pdf/
 *      *      applying_apple_hdr_effect_to_your_photos
 *      *  [1] https://docs.google.com/document/d/1iUpYAThVV_FuDdeiO3t0vnlfoA1ryq0WfGS9FuydwKc
 *      */
 *     enum class Type {
 *         kDefault,
 *         kApple,
 *     };
 *     Type fType = Type::kDefault;
 *
 *     /**
 *      * If specified, color space to apply the gainmap in, otherwise the base image's color space
 *      * is used. Only the color primaries are used, the transfer function is irrelevant.
 *      */
 *     sk_sp<SkColorSpace> fGainmapMathColorSpace = nullptr;
 *
 *     /**
 *      * Return true if this can be encoded as an UltraHDR v1 image.
 *      */
 *     bool isUltraHDRv1Compatible() const;
 *
 *     /**
 *      * If |data| contains an ISO 21496-1 version that is supported, return true. Otherwise return
 *      * false.
 *      */
 *     static bool ParseVersion(const SkData* data);
 *
 *     /**
 *      * If |data| constains ISO 21496-1 metadata then parse that metadata then use it to populate
 *      * |info| and return true, otherwise return false. If |data| indicates that that the base image
 *      * color space primaries should be used for gainmap application then set
 *      * |fGainmapMathColorSpace| to nullptr, otherwise set |fGainmapMathColorSpace| to sRGB (the
 *      * default, to be overwritten by the image decoder).
 *      */
 *     static bool Parse(const SkData* data, SkGainmapInfo& info);
 *
 *     /**
 *      * Serialize an ISO 21496-1 version 0 blob containing only the version structure.
 *      */
 *     static sk_sp<SkData> SerializeVersion();
 *
 *     /**
 *      * Serialize an ISO 21496-1 version 0 blob containing this' gainmap parameters.
 *      */
 *     sk_sp<SkData> serialize() const;
 *
 *     inline bool operator==(const SkGainmapInfo& other) const {
 *         return fGainmapRatioMin == other.fGainmapRatioMin &&
 *                fGainmapRatioMax == other.fGainmapRatioMax && fGainmapGamma == other.fGainmapGamma &&
 *                fEpsilonSdr == other.fEpsilonSdr && fEpsilonHdr == other.fEpsilonHdr &&
 *                fDisplayRatioSdr == other.fDisplayRatioSdr &&
 *                fDisplayRatioHdr == other.fDisplayRatioHdr &&
 *                fBaseImageType == other.fBaseImageType && fType == other.fType &&
 *                SkColorSpace::Equals(fGainmapMathColorSpace.get(),
 *                                     other.fGainmapMathColorSpace.get());
 *     }
 *     inline bool operator!=(const SkGainmapInfo& other) const { return !(*this == other); }
 * }
 * ```
 */
public data class SkGainmapInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fGainmapRatioMin
   * ```
   */
  public var fGainmapRatioMin: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fGainmapRatioMax
   * ```
   */
  public var fGainmapRatioMax: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fGainmapGamma
   * ```
   */
  public var fGainmapGamma: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fEpsilonSdr
   * ```
   */
  public var fEpsilonSdr: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fEpsilonHdr
   * ```
   */
  public var fEpsilonHdr: Int,
  /**
   * C++ original:
   * ```cpp
   * float fDisplayRatioSdr = 1.f
   * ```
   */
  public var fDisplayRatioSdr: Float,
  /**
   * C++ original:
   * ```cpp
   * float fDisplayRatioHdr = 2.f
   * ```
   */
  public var fDisplayRatioHdr: Float,
  /**
   * C++ original:
   * ```cpp
   * BaseImageType fBaseImageType = BaseImageType::kSDR
   * ```
   */
  public var fBaseImageType: BaseImageType,
  /**
   * C++ original:
   * ```cpp
   * Type fType = Type::kDefault
   * ```
   */
  public var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorSpace> fGainmapMathColorSpace
   * ```
   */
  public var fGainmapMathColorSpace: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkGainmapInfo::isUltraHDRv1Compatible() const {
   *     // UltraHDR v1 supports having the base image be HDR in theory, but it is largely
   *     // untested.
   *     if (fBaseImageType == BaseImageType::kHDR) {
   *         return false;
   *     }
   *     // UltraHDR v1 doesn't support a non-base gainmap math color space.
   *     if (fGainmapMathColorSpace) {
   *         return false;
   *     }
   *     return true;
   * }
   * ```
   */
  public fun isUltraHDRv1Compatible(): Boolean {
    TODO("Implement isUltraHDRv1Compatible")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> SkGainmapInfo::serialize() const {
   *     SkDynamicMemoryWStream s;
   *     // Version.
   *     SkStreamPriv::WriteU16BE(&s, 0);  // Minimum reader version
   *     SkStreamPriv::WriteU16BE(&s, 0);  // Writer version
   *
   *     // Flags.
   *     bool all_single_channel = is_single_channel(fGainmapRatioMin) &&
   *                               is_single_channel(fGainmapRatioMax) &&
   *                               is_single_channel(fGainmapGamma) && is_single_channel(fEpsilonSdr) &&
   *                               is_single_channel(fEpsilonHdr);
   *     uint8_t flags = 0;
   *     if (!fGainmapMathColorSpace) {
   *         flags |= kUseBaseColourSpaceMask;
   *     }
   *     if (!all_single_channel) {
   *         flags |= kIsMultiChannelMask;
   *     }
   *     s.write8(flags);
   *
   *     // Base and altr headroom.
   *     switch (fBaseImageType) {
   *         case SkGainmapInfo::BaseImageType::kSDR:
   *             write_positive_rational_be(s, std::log2(fDisplayRatioSdr));
   *             write_positive_rational_be(s, std::log2(fDisplayRatioHdr));
   *             break;
   *         case SkGainmapInfo::BaseImageType::kHDR:
   *             write_positive_rational_be(s, std::log2(fDisplayRatioHdr));
   *             write_positive_rational_be(s, std::log2(fDisplayRatioSdr));
   *             break;
   *     }
   *
   *     // Per-channel information.
   *     for (int i = 0; i < (all_single_channel ? 1 : 3); ++i) {
   *         write_rational_be(s, std::log2(fGainmapRatioMin[i]));
   *         write_rational_be(s, std::log2(fGainmapRatioMax[i]));
   *         write_positive_rational_be(s, 1.f / fGainmapGamma[i]);
   *         switch (fBaseImageType) {
   *             case SkGainmapInfo::BaseImageType::kSDR:
   *                 write_rational_be(s, fEpsilonSdr[i]);
   *                 write_rational_be(s, fEpsilonHdr[i]);
   *                 break;
   *             case SkGainmapInfo::BaseImageType::kHDR:
   *                 write_rational_be(s, fEpsilonHdr[i]);
   *                 write_rational_be(s, fEpsilonSdr[i]);
   *                 break;
   *         }
   *     }
   *     return s.detachAsData();
   * }
   * ```
   */
  public fun serialize(): Int {
    TODO("Implement serialize")
  }

  /**
   * C++ original:
   * ```cpp
   * inline bool operator==(const SkGainmapInfo& other) const {
   *         return fGainmapRatioMin == other.fGainmapRatioMin &&
   *                fGainmapRatioMax == other.fGainmapRatioMax && fGainmapGamma == other.fGainmapGamma &&
   *                fEpsilonSdr == other.fEpsilonSdr && fEpsilonHdr == other.fEpsilonHdr &&
   *                fDisplayRatioSdr == other.fDisplayRatioSdr &&
   *                fDisplayRatioHdr == other.fDisplayRatioHdr &&
   *                fBaseImageType == other.fBaseImageType && fType == other.fType &&
   *                SkColorSpace::Equals(fGainmapMathColorSpace.get(),
   *                                     other.fGainmapMathColorSpace.get());
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  public enum class BaseImageType {
    kSDR,
    kHDR,
  }

  public enum class Type {
    kDefault,
    kApple,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * bool SkGainmapInfo::ParseVersion(const SkData* data) {
     *     if (!data) {
     *         return false;
     *     }
     *     auto s = SkMemoryStream::MakeDirect(data->data(), data->size());
     *     return read_iso_gainmap_version(s.get());
     * }
     * ```
     */
    public fun parseVersion(`data`: SkData?): Boolean {
      TODO("Implement parseVersion")
    }

    /**
     * C++ original:
     * ```cpp
     * bool SkGainmapInfo::Parse(const SkData* data, SkGainmapInfo& info) {
     *     if (!data) {
     *         return false;
     *     }
     *     auto s = SkMemoryStream::MakeDirect(data->data(), data->size());
     *     return read_iso_gainmap_info(s.get(), info);
     * }
     * ```
     */
    public fun parse(`data`: SkData?, info: SkGainmapInfo): Boolean {
      TODO("Implement parse")
    }

    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkData> SkGainmapInfo::SerializeVersion() {
     *     SkDynamicMemoryWStream s;
     *     SkStreamPriv::WriteU16BE(&s, 0);  // Minimum reader version
     *     SkStreamPriv::WriteU16BE(&s, 0);  // Writer version
     *     return s.detachAsData();
     * }
     * ```
     */
    public fun serializeVersion(): Int {
      TODO("Implement serializeVersion")
    }
  }
}
