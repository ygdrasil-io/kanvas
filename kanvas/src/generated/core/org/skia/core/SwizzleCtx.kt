package org.skia.core

import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct SwizzleCtx {
 *     // If we are processing more than 16 pixels at a time, an 8-bit offset won't be sufficient and
 *     // `offsets` will need to use uint16_t (or dial down the premultiplication).
 *     static_assert(kMaxStride_highp <= 16);
 *
 *     SkRPOffset dst;
 *     uint8_t offsets[4];  // values must be byte offsets (4 * highp-stride * component-index)
 * }
 * ```
 */
public data class SwizzleCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkRPOffset dst
   * ```
   */
  public var dst: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t offsets[4]
   * ```
   */
  public var offsets: IntArray,
)
