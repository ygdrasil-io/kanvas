package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SamplerDesc {
 *     static_assert(kSkTileModeCount <= 4 && kSkFilterModeCount <= 2 && kSkMipmapModeCount <= 4);
 *
 *     constexpr SamplerDesc(const SkSamplingOptions& samplingOptions, SkTileMode tileMode)
 *             : SamplerDesc(samplingOptions, {tileMode, tileMode}) {}
 *
 *     constexpr SamplerDesc(const SkSamplingOptions& samplingOptions,
 *                           const std::pair<SkTileMode, SkTileMode> tileModes,
 *                           const ImmutableSamplerInfo info = {})
 *             : fDesc((static_cast<int>(tileModes.first)            << kTileModeXShift           ) |
 *                     (static_cast<int>(tileModes.second)           << kTileModeYShift           ) |
 *                     (static_cast<int>(samplingOptions.filter)     << kFilterModeShift          ) |
 *                     (static_cast<int>(samplingOptions.mipmap)     << kMipmapModeShift          ) |
 *                     (info.fNonFormatYcbcrConversionInfo           << kImmutableSamplerInfoShift) )
 *             , fFormat(info.fFormat)
 *             , fExternalFormatMostSignificantBits(info.fFormat >> 32) {
 *
 *         // Cubic sampling is handled in a shader, with the actual texture sampled by with NN,
 *         // but that is what a cubic SkSamplingOptions is set to if you ignore 'cubic', which let's
 *         // us simplify how we construct SamplerDec's from the options passed to high-level draws.
 *         SkASSERT(!samplingOptions.useCubic || (samplingOptions.filter == SkFilterMode::kNearest &&
 *                                                samplingOptions.mipmap == SkMipmapMode::kNone));
 *
 *         // TODO: Add aniso value when used.
 *
 *         // Assert that fYcbcrConversionInfo does not exceed kMaxNumConversionInfoBits such that
 *         // the conversion information can fit within an uint32.
 *         SkASSERT(info.fNonFormatYcbcrConversionInfo >> kMaxNumConversionInfoBits == 0);
 *     }
 *     constexpr SamplerDesc() = default;
 *     constexpr SamplerDesc(const SamplerDesc&) = default;
 *     constexpr SamplerDesc& operator=(const SamplerDesc&) = default;
 *
 *     constexpr SamplerDesc(uint32_t desc, uint32_t format, uint32_t extFormatMSB)
 *             : fDesc(desc)
 *             , fFormat(format)
 *             , fExternalFormatMostSignificantBits(extFormatMSB) {}
 *
 *     bool operator==(const SamplerDesc& o) const {
 *         return o.fDesc == fDesc && o.fFormat == fFormat &&
 *                o.fExternalFormatMostSignificantBits == fExternalFormatMostSignificantBits;
 *     }
 *
 *     bool operator!=(const SamplerDesc& o) const { return !(*this == o); }
 *
 *     SkTileMode tileModeX()          const {
 *         return static_cast<SkTileMode>((fDesc >> kTileModeXShift) & 0b11);
 *     }
 *     SkTileMode tileModeY()          const {
 *         return static_cast<SkTileMode>((fDesc >> kTileModeYShift) & 0b11);
 *     }
 *     SkFilterMode filterMode()       const {
 *         return static_cast<SkFilterMode>((fDesc >> kFilterModeShift) & 0b01);
 *     }
 *     SkMipmapMode mipmap()           const {
 *         return static_cast<SkMipmapMode>((fDesc >> kMipmapModeShift) & 0b11);
 *     }
 *     uint32_t   desc()               const { return fDesc;                                        }
 *     uint32_t   format()             const { return fFormat;                                      }
 *     uint32_t   externalFormatMSBs() const { return fExternalFormatMostSignificantBits;           }
 *     bool       isImmutable()        const { return (fDesc >> kImmutableSamplerInfoShift) != 0;   }
 *     bool       usesExternalFormat() const { return (fDesc >> kImmutableSamplerInfoShift) & 0b1;  }
 *
 *     // NOTE: returns the HW sampling options to use, so a bicubic SkSamplingOptions will become
 *     // nearest-neighbor sampling in HW.
 *     SkSamplingOptions samplingOptions() const {
 *         // TODO: Add support for anisotropic filtering
 *         SkFilterMode filter = static_cast<SkFilterMode>((fDesc >> kFilterModeShift) & 0b01);
 *         SkMipmapMode mipmap = static_cast<SkMipmapMode>((fDesc >> kMipmapModeShift) & 0b11);
 *         return SkSamplingOptions(filter, mipmap);
 *     }
 *
 *     ImmutableSamplerInfo immutableSamplerInfo() const {
 *         return {this->desc() >> kImmutableSamplerInfoShift,
 *                 ((uint64_t) this->externalFormatMSBs() << 32) | (uint64_t) this->format()};
 *     }
 *
 *     SkSpan<const uint32_t> asSpan() const {
 *         // Span length depends upon whether the sampler is immutable and if it uses a known format
 *         return {&fDesc, 1u + this->isImmutable() + this->usesExternalFormat()};
 *     }
 *
 *     // These are public such that backends can bitshift data in order to determine whatever
 *     // sampler qualities they need from fDesc.
 *     static constexpr int kNumTileModeBits   = SkNextLog2_portable(int(SkTileMode::kLastTileMode)+1);
 *     static constexpr int kNumFilterModeBits = SkNextLog2_portable(int(SkFilterMode::kLast)+1);
 *     static constexpr int kNumMipmapModeBits = SkNextLog2_portable(int(SkMipmapMode::kLast)+1);
 *     static constexpr int kMaxNumConversionInfoBits =
 *             32 - kNumFilterModeBits - kNumMipmapModeBits - kNumTileModeBits;
 *
 *     static constexpr int kTileModeXShift            = 0;
 *     static constexpr int kTileModeYShift            = kTileModeXShift  + kNumTileModeBits;
 *     static constexpr int kFilterModeShift           = kTileModeYShift  + kNumTileModeBits;
 *     static constexpr int kMipmapModeShift           = kFilterModeShift + kNumFilterModeBits;
 *     static constexpr int kImmutableSamplerInfoShift = kMipmapModeShift + kNumMipmapModeBits;
 *
 *     // Only relevant when using immutable samplers. Otherwise, can be ignored. The number of uint32s
 *     // required to represent all relevant sampler desc information depends upon whether we are using
 *     // a known or external format.
 *     static constexpr int kInt32sNeededKnownFormat = 2;
 *     static constexpr int kInt32sNeededExternalFormat = 3;
 *
 * private:
 *     // Note: The order of these member attributes matters to keep unique object representation
 *     // such that SkGoodHash can be used to hash SamplerDesc objects.
 *     uint32_t fDesc = 0;
 *
 *     // Data fields populated by backend Caps which store texture format information (needed for
 *     // YCbCr sampling). Only relevant when using immutable samplers. Otherwise, can be ignored.
 *     // Known formats only require a uint32, but external formats can be up to a uint64. We store
 *     // this as two separate uint32s such that has_unique_object_representation can be true, allowing
 *     // this structure to be easily hashed using SkGoodHash. So, external formats can be represented
 *     // with (fExternalFormatMostSignificantBits << 32) | fFormat.
 *     uint32_t fFormat = 0;
 *     uint32_t fExternalFormatMostSignificantBits = 0;
 * }
 * ```
 */
public data class SamplerDesc public constructor(
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kNumTileModeBits
   * ```
   */
  private var fDesc: Int,
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kNumFilterModeBits
   * ```
   */
  private var fFormat: Int,
  /**
   * C++ original:
   * ```cpp
   * static constexpr int kNumMipmapModeBits
   * ```
   */
  private var fExternalFormatMostSignificantBits: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr SamplerDesc& operator=(const SamplerDesc&) = default
   * ```
   */
  public fun assign(param0: SamplerDesc) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SamplerDesc& o) const {
   *         return o.fDesc == fDesc && o.fFormat == fFormat &&
   *                o.fExternalFormatMostSignificantBits == fExternalFormatMostSignificantBits;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SamplerDesc& o) const { return !(*this == o); }
   * ```
   */
  public fun tileModeX(): Int {
    TODO("Implement tileModeX")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode tileModeX()          const {
   *         return static_cast<SkTileMode>((fDesc >> kTileModeXShift) & 0b11);
   *     }
   * ```
   */
  public fun tileModeY(): Int {
    TODO("Implement tileModeY")
  }

  /**
   * C++ original:
   * ```cpp
   * SkTileMode tileModeY()          const {
   *         return static_cast<SkTileMode>((fDesc >> kTileModeYShift) & 0b11);
   *     }
   * ```
   */
  public fun filterMode(): Int {
    TODO("Implement filterMode")
  }

  /**
   * C++ original:
   * ```cpp
   * SkFilterMode filterMode()       const {
   *         return static_cast<SkFilterMode>((fDesc >> kFilterModeShift) & 0b01);
   *     }
   * ```
   */
  public fun mipmap(): Int {
    TODO("Implement mipmap")
  }

  /**
   * C++ original:
   * ```cpp
   * SkMipmapMode mipmap()           const {
   *         return static_cast<SkMipmapMode>((fDesc >> kMipmapModeShift) & 0b11);
   *     }
   * ```
   */
  public fun desc(): Int {
    TODO("Implement desc")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t   desc()               const { return fDesc;                                        }
   * ```
   */
  public fun format(): Int {
    TODO("Implement format")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t   format()             const { return fFormat;                                      }
   * ```
   */
  public fun externalFormatMSBs(): Int {
    TODO("Implement externalFormatMSBs")
  }

  /**
   * C++ original:
   * ```cpp
   * uint32_t   externalFormatMSBs() const { return fExternalFormatMostSignificantBits;           }
   * ```
   */
  public fun isImmutable(): Boolean {
    TODO("Implement isImmutable")
  }

  /**
   * C++ original:
   * ```cpp
   * bool       isImmutable()        const { return (fDesc >> kImmutableSamplerInfoShift) != 0;   }
   * ```
   */
  public fun usesExternalFormat(): Boolean {
    TODO("Implement usesExternalFormat")
  }

  /**
   * C++ original:
   * ```cpp
   * bool       usesExternalFormat() const { return (fDesc >> kImmutableSamplerInfoShift) & 0b1;  }
   * ```
   */
  public fun samplingOptions(): Int {
    TODO("Implement samplingOptions")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions samplingOptions() const {
   *         // TODO: Add support for anisotropic filtering
   *         SkFilterMode filter = static_cast<SkFilterMode>((fDesc >> kFilterModeShift) & 0b01);
   *         SkMipmapMode mipmap = static_cast<SkMipmapMode>((fDesc >> kMipmapModeShift) & 0b11);
   *         return SkSamplingOptions(filter, mipmap);
   *     }
   * ```
   */
  public fun immutableSamplerInfo(): ImmutableSamplerInfo {
    TODO("Implement immutableSamplerInfo")
  }

  /**
   * C++ original:
   * ```cpp
   * ImmutableSamplerInfo immutableSamplerInfo() const {
   *         return {this->desc() >> kImmutableSamplerInfoShift,
   *                 ((uint64_t) this->externalFormatMSBs() << 32) | (uint64_t) this->format()};
   *     }
   * ```
   */
  public fun asSpan(): Int {
    TODO("Implement asSpan")
  }

  public companion object {
    public val kNumTileModeBits: Int = TODO("Initialize kNumTileModeBits")

    public val kNumFilterModeBits: Int = TODO("Initialize kNumFilterModeBits")

    public val kNumMipmapModeBits: Int = TODO("Initialize kNumMipmapModeBits")

    public val kMaxNumConversionInfoBits: Int = TODO("Initialize kMaxNumConversionInfoBits")

    public val kTileModeXShift: Int = TODO("Initialize kTileModeXShift")

    public val kTileModeYShift: Int = TODO("Initialize kTileModeYShift")

    public val kFilterModeShift: Int = TODO("Initialize kFilterModeShift")

    public val kMipmapModeShift: Int = TODO("Initialize kMipmapModeShift")

    public val kImmutableSamplerInfoShift: Int = TODO("Initialize kImmutableSamplerInfoShift")

    public val kInt32sNeededKnownFormat: Int = TODO("Initialize kInt32sNeededKnownFormat")

    public val kInt32sNeededExternalFormat: Int = TODO("Initialize kInt32sNeededExternalFormat")
  }
}
