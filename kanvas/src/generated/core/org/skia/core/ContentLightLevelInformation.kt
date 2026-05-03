package org.skia.core

import kotlin.Any
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct SK_API ContentLightLevelInformation {
 *     float fMaxCLL = 0.f;
 *     float fMaxFALL = 0.f;
 *
 *     /**
 *      * Decode from the binary encoding listed at:
 *      *   AV1 Bitstream & Decoding Process Specification Version 1.0.0 Errata 1
 *      *   https://aomediacodec.github.io/av1-spec/av1-spec.pdf
 *      *   5.8.3 Metadata high dynamic range content light level syntax
 *      * This encoding is equivalent to:
 *      *   ITU-T H.265 (V10) (07/2024)
 *      *   D.2.35 Content light level information SEI message syntax
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
 *      * Decode from the binary encoding listed at:
 *      *   Portable Network Graphics (PNG) Specification (Third Edition)
 *      *   11.3.2.8 cLLI Content Light Level Information
 *      *   https://www.w3.org/TR/png-3/#cLLI-chunk
 *      * This encoding is not equivalent to the encoding used by parse().
 *      * Return false if parsing fails.
 *      */
 *     bool parsePngChunk(const SkData* data);
 *
 *     /**
 *      * Serialize to the encoding used by parsePngChunk().
 *      */
 *     sk_sp<SkData> serializePngChunk() const;
 *
 *     /**
 *      * Return a human-readable description.
 *      */
 *     SkString toString() const;
 *
 *     bool operator==(const ContentLightLevelInformation& other) const;
 *     bool operator!=(const ContentLightLevelInformation& other) const {
 *         return !(*this == other);
 *     }
 * }
 * ```
 */
public data class ContentLightLevelInformation public constructor(
  /**
   * C++ original:
   * ```cpp
   * float fMaxCLL = 0.f
   * ```
   */
  public var fMaxCLL: Float,
  /**
   * C++ original:
   * ```cpp
   * float fMaxFALL = 0.f
   * ```
   */
  public var fMaxFALL: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * bool ContentLightLevelInformation::parse(const SkData* data) {
   *     if (data->size() != 4) {
   *         return false;
   *     }
   *     SkMemoryStream s(data->data(), data->size());
   *
   *     uint16_t max_cll = 0;
   *     uint16_t max_fall = 0;
   *     if (!SkStreamPriv::ReadU16BE(&s, &max_cll)) {
   *         return false;
   *     }
   *     if (!SkStreamPriv::ReadU16BE(&s, &max_fall)) {
   *         return false;
   *     }
   *
   *     fMaxCLL = max_cll;
   *     fMaxFALL = max_fall;
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
   * sk_sp<SkData> ContentLightLevelInformation::serialize() const {
   *     SkDynamicMemoryWStream s;
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fMaxCLL));
   *     SkStreamPriv::WriteU16BE(&s, std::llroundf(fMaxFALL));
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
   * bool ContentLightLevelInformation::parsePngChunk(const SkData* data) {
   *     if (data->size() != 8) {
   *         return false;
   *     }
   *     SkMemoryStream s(data->data(), data->size());
   *
   *     uint32_t max_cll_times_10000 = 0;
   *     uint32_t max_fall_times_10000 = 0;
   *     if (!SkStreamPriv::ReadU32BE(&s, &max_cll_times_10000)) {
   *         return false;
   *     }
   *     if (!SkStreamPriv::ReadU32BE(&s, &max_fall_times_10000)) {
   *         return false;
   *     }
   *
   *     fMaxCLL = max_cll_times_10000 / clli_png_luminance_divisor;
   *     fMaxFALL = max_fall_times_10000 / clli_png_luminance_divisor;
   *     return true;
   * }
   * ```
   */
  public fun parsePngChunk(`data`: SkData?): Boolean {
    TODO("Implement parsePngChunk")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkData> ContentLightLevelInformation::serializePngChunk() const {
   *     SkDynamicMemoryWStream s;
   *     SkStreamPriv::WriteU32BE(&s, std::llroundf(fMaxCLL * clli_png_luminance_divisor));
   *     SkStreamPriv::WriteU32BE(&s, std::llroundf(fMaxFALL * clli_png_luminance_divisor));
   *     return s.detachAsData();
   * }
   * ```
   */
  public fun serializePngChunk(): Int {
    TODO("Implement serializePngChunk")
  }

  /**
   * C++ original:
   * ```cpp
   * SkString ContentLightLevelInformation::toString() const {
   *     return SkStringPrintf("{maxCLL:%f, maxFALL:%f}", fMaxCLL, fMaxFALL);
   * }
   * ```
   */
  public override fun toString(): String {
    TODO("Implement toString")
  }

  /**
   * C++ original:
   * ```cpp
   * bool ContentLightLevelInformation::operator==(const ContentLightLevelInformation& other) const {
   *     return fMaxCLL == other.fMaxCLL &&
   *            fMaxFALL == other.fMaxFALL;
   * }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
