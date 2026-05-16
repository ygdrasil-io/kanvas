package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct EvenlySpaced2StopGradientCtx {
 *     float factor[kRGBAChannels];
 *     float bias[kRGBAChannels];
 * }
 * ```
 */
public data class EvenlySpaced2StopGradientCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * float factor[kRGBAChannels]
   * ```
   */
  public var factor: Int,
  /**
   * C++ original:
   * ```cpp
   * float bias[kRGBAChannels]
   * ```
   */
  public var bias: Int,
)
