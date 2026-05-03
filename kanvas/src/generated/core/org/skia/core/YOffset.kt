package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct YOffset {
 *     int32_t  fY;
 *     uint32_t fOffset;
 * }
 * ```
 */
public data class YOffset public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t  fY
   * ```
   */
  public var fY: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fOffset
   * ```
   */
  public var fOffset: Int,
)
