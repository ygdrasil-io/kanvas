package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SamplerIndex { uint32_t fValue; }
 * ```
 */
public data class SamplerIndex public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fValue
   * ```
   */
  public var fValue: Int,
)
