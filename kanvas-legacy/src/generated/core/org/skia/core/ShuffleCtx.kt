package org.skia.core

import kotlin.Int
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct ShuffleCtx {
 *     int32_t* ptr;
 *     int count;
 *     uint16_t offsets[16];  // values must be byte offsets (4 * highp-stride * component-index)
 * }
 * ```
 */
public data class ShuffleCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t* ptr
   * ```
   */
  public var ptr: Int?,
  /**
   * C++ original:
   * ```cpp
   * int count
   * ```
   */
  public var count: Int,
  /**
   * C++ original:
   * ```cpp
   * uint16_t offsets[16]
   * ```
   */
  public var offsets: IntArray,
)
