package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ImmutableSamplerInfo {
 *     // If the sampler requires YCbCr conversion, backends can place that information here.
 *     // In order to fit within SamplerDesc's uint32 desc field, backends can only utilize up to
 *     // kMaxNumConversionInfoBits bits.
 *     uint32_t fNonFormatYcbcrConversionInfo = 0;
 *     // fFormat represents known OR external format numerical representation.
 *     uint64_t fFormat = 0;
 * }
 * ```
 */
public data class ImmutableSamplerInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fNonFormatYcbcrConversionInfo
   * ```
   */
  public var fNonFormatYcbcrConversionInfo: Int,
  /**
   * C++ original:
   * ```cpp
   * uint64_t fFormat
   * ```
   */
  public var fFormat: Int,
)
