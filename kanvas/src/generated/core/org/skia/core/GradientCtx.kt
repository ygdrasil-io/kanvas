package org.skia.core

import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct GradientCtx {
 *     size_t stopCount;
 *     float* factors[kRGBAChannels];
 *     float* biases[kRGBAChannels];
 *     float* ts;
 * }
 * ```
 */
public data class GradientCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t stopCount
   * ```
   */
  public var stopCount: Int,
  /**
   * C++ original:
   * ```cpp
   * float* factors[kRGBAChannels]
   * ```
   */
  public var factors: Int,
  /**
   * C++ original:
   * ```cpp
   * float* biases[kRGBAChannels]
   * ```
   */
  public var biases: Int,
  /**
   * C++ original:
   * ```cpp
   * float* ts
   * ```
   */
  public var ts: Float?,
)
