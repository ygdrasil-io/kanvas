package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.Unit

/**
 * C++ original:
 * ```cpp
 * struct GatherCtx {
 *     const void* pixels;
 *     int         stride;
 *     float       width;
 *     float       height;
 *     float       weights[16];  // for bicubic and bicubic_clamp_8888
 *     // Controls whether pixel i-1 or i is selected when floating point sample position is exactly i.
 *     bool        roundDownAtInteger = false;
 * }
 * ```
 */
public data class GatherCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * const void* pixels
   * ```
   */
  public val pixels: Unit?,
  /**
   * C++ original:
   * ```cpp
   * int         stride
   * ```
   */
  public var stride: Int,
  /**
   * C++ original:
   * ```cpp
   * float       width
   * ```
   */
  public var width: Float,
  /**
   * C++ original:
   * ```cpp
   * float       height
   * ```
   */
  public var height: Float,
  /**
   * C++ original:
   * ```cpp
   * float       weights[16]
   * ```
   */
  public var weights: FloatArray,
  /**
   * C++ original:
   * ```cpp
   * bool        roundDownAtInteger = false
   * ```
   */
  public var roundDownAtInteger: Boolean,
)
