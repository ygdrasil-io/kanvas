package org.skia.gpu

import kotlin.Float
import kotlin.String
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * struct ReducedBlendModeInfo {
 *     const char*         fFunction;
 *     SkSpan<const float> fUniformData;
 * }
 * ```
 */
public data class ReducedBlendModeInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char*         fFunction
   * ```
   */
  public val fFunction: String?,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float> fUniformData
   * ```
   */
  public val fUniformData: SkSpan<Float>,
)
