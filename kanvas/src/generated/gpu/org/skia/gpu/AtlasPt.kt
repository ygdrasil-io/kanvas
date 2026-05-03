package org.skia.gpu

import kotlin.UShort

/**
 * C++ original:
 * ```cpp
 * struct AtlasPt {
 *     uint16_t u;
 *     uint16_t v;
 * }
 * ```
 */
public data class AtlasPt public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint16_t u
   * ```
   */
  public var u: UShort,
  /**
   * C++ original:
   * ```cpp
   * uint16_t v
   * ```
   */
  public var v: UShort,
)
