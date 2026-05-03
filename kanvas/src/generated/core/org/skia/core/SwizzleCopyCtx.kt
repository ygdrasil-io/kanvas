package org.skia.core

import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct SwizzleCopyCtx {
 *     int32_t* dst;
 *     const int32_t* src;   // src values must _not_ overlap dst values
 *     uint16_t offsets[4];  // values must be byte offsets (4 * highp-stride * component-index)
 * }
 * ```
 */
public data class SwizzleCopyCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t* dst
   * ```
   */
  public var dst: Int?,
  /**
   * C++ original:
   * ```cpp
   * const int32_t* src
   * ```
   */
  public val src: Int?,
  /**
   * C++ original:
   * ```cpp
   * uint16_t offsets[4]
   * ```
   */
  public var offsets: IntArray,
)
