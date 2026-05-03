package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct SK_API MasteringDisplayColorVolume {
 *     SkColorSpacePrimaries fDisplayPrimaries = {0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f, 0.f};
 *     float fMaximumDisplayMasteringLuminance = 0.f;
 *     float fMinimumDisplayMasteringLuminance = 0.f;
 *
 *     /**
 *      * The encoding as defined in:
 *      *   AV1 Bitstream & Decoding Process Specification Version 1.0.0 Errata 1
 *      *   https://aomediacodec.github.io/av1-spec/av1-spec.pdf
 *      *   5.8.4 Metadata high dynamic range mastering display color volume syntax
 *      * This encoding is equivalent to:
 *      *   ITU-T H.265 (V10) (07/2024)
 *      *   D.2.35 Content light level information SEI message syntax
 *      * This encoding is also equivalent to:
 *      *   Portable Network Graphics (PNG) Specification (Third Edition)
 *      *   11.3.2.7 mDCV Mastering Display Color Volume
 *      *   https://www.w3.org/TR/png-3/#mDCV-chunk
 *      * Return false if parsing fails.
 *      */
 *     bool parse(const SkData* data);
 *
 *     /**
 *      * Serialize to the encoding used by parse().
 *      */
 *     sk_sp<SkData> serialize() const;
 *
 *     /**
 *      * Return a human-readable description.
 *      */
 *     SkString toString() const;
 *
 *     bool operator==(const MasteringDisplayColorVolume& other) const;
 *     bool operator!=(const MasteringDisplayColorVolume& other) const {
 *         return !(*this == other);
 *     }
 * }
 * ```
 */
public data class MasteringDisplayColorVolume public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkColorSpacePrimaries fDisplayPrimaries
   * ```
   */
  public var fDisplayPrimaries: Int,
  /**
   * C++ original:
   * ```cpp
   * float fMaximumDisplayMasteringLuminance = 0.f
   * ```
   */
  public var fMaximumDisplayMasteringLuminance: Float,
  /**
   * C++ original:
   * ```cpp
   * float fMinimumDisplayMasteringLuminance = 0.f
   * ```
   */
  public var fMinimumDisplayMasteringLuminance: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * bool MasteringDisplayColorVolume::parse(const SkData* data) {
   *     if (data->size() != 24) {
   *         return false;
   *     }
   *     SkMemoryStream s(data->data(), data->size());
   *
   *     uint16_t chromaticities_times_50000[8];
   *     for (auto& chromaticity_times_50000 : chromaticities_times_50000) {
   *         if (!SkStreamPriv::ReadU16BE(&s, &chromaticity_times_50000)) {
   *             return false;
   *         }
   *     }
   *     uint32_t max_luminance_times_10000 = 0;
   *     uint32_t min_luminance_times_10000 = 0;
   *     if (!SkStreamPriv::ReadU32BE(&s, &max_luminance_times_10000)) {
   *         return false;
   *     }
   *     if (!SkStreamPriv::ReadU32BE(&s, &min_luminance_times_10000)) {
   *         return false;
   *     }
   *
   *     fDisplayPrimaries = SkColorSpacePrimaries({
   *         chromaticities_times_50000[0] / mdcv_chrominance_divisor,
   *         chromaticities_times_50000[1] / mdcv_chrominance_divisor,
   *         chromaticities_times_50000[2] / mdcv_chrominance_divisor,
   *         chromaticities_times_50000[3] / mdcv_chrominance_divisor,
   *         chromaticities_times_50000[4] / mdcv_chrominance_divisor,
   *         chromaticities_times_50000[5] / mdcv_chrominance_divisor,
   *         chromaticities_times_50000[6] / mdcv_chrominance_divisor,
   *         chromaticities_times_50000[7] / mdcv_chrominance_divisor,
   *     });
   *     fMaximumDisplayMasteringLuminance = max_luminance_times_10000 / mdcv_luminance_divisor;
   *     fMinimumDisplayMasteringLuminance = min_luminance_times_10000 / mdcv_luminance_divisor;
   *     return true;
   * }
   * ```
   */
  public fun parse(`data`: SkData?): Boolean {
    TODO("Implement parse")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> MasteringDisplayColorVolume::serialize() const {
   *     SkDynamicMemoryWStream s;
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fRX * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fRY * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fGX * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fGY * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fBX * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fBY * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fWX * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fDisplayPrimaries.fWY * mdcv_chrominance_divisor));
   *     SkStreamPriv::WriteU32BE(
   *             &s, std::llroundf(fMaximumDisplayMasteringLuminance * mdcv_luminance_divisor));
   *     SkStreamPriv::WriteU32BE(
   *             &s, std::llroundf(fMinimumDisplayMasteringLuminance * mdcv_luminance_divisor));
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
   * SkString MasteringDisplayColorVolume::toString() const {
   *     return SkStringPrintf(
   *         "{red:[%1.8f,%1.8f], green:[%1.8f,%1.8f], blue:[%1.8f,%1.8f], white:[%1.8f,%1.8f], maxLum:%f, minLum:%f}",
   *         fDisplayPrimaries.fRX, fDisplayPrimaries.fRY,
   *         fDisplayPrimaries.fGX, fDisplayPrimaries.fGY,
   *         fDisplayPrimaries.fBX, fDisplayPrimaries.fBY,
   *         fDisplayPrimaries.fWX, fDisplayPrimaries.fWY,
   *         fMaximumDisplayMasteringLuminance,
   *         fMinimumDisplayMasteringLuminance);
   * }
   * ```
   */
  public override fun toString(): String {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * bool MasteringDisplayColorVolume::operator==(const MasteringDisplayColorVolume& other) const {
   *     return fDisplayPrimaries.fRX == other.fDisplayPrimaries.fRX &&
   *            fDisplayPrimaries.fRY == other.fDisplayPrimaries.fRY &&
   *            fDisplayPrimaries.fGX == other.fDisplayPrimaries.fGX &&
   *            fDisplayPrimaries.fGY == other.fDisplayPrimaries.fGY &&
   *            fDisplayPrimaries.fBX == other.fDisplayPrimaries.fBX &&
   *            fDisplayPrimaries.fBY == other.fDisplayPrimaries.fBY &&
   *            fDisplayPrimaries.fWX == other.fDisplayPrimaries.fWX &&
   *            fDisplayPrimaries.fWY == other.fDisplayPrimaries.fWY &&
   *            fMaximumDisplayMasteringLuminance == other.fMaximumDisplayMasteringLuminance &&
   *            fMinimumDisplayMasteringLuminance == other.fMinimumDisplayMasteringLuminance;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
