package org.skia.tests

import kotlin.Float
import kotlin.Int
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * struct UniformData {
 *     std::string_view    name;
 *     SkSpan<const float> span;
 * }
 * ```
 */
public data class UniformData public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::string_view    name
   * ```
   */
  public var name: Int,
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const float> span
   * ```
   */
  public val span: SkSpan<Float>,
)
