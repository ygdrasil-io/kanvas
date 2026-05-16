package org.skia.core

import kotlin.Float
import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct UniformColorCtx {
 *     float r,g,b,a;
 *     uint16_t rgba[4];  // [0,255] in a 16-bit lane.
 * }
 * ```
 */
public data class UniformColorCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * float r
   * ```
   */
  public var r: Float,
  /**
   * C++ original:
   * ```cpp
   * float r,g
   * ```
   */
  public var g: Float,
  /**
   * C++ original:
   * ```cpp
   * float r,g,b
   * ```
   */
  public var b: Float,
  /**
   * C++ original:
   * ```cpp
   * float r,g,b,a
   * ```
   */
  public var a: Float,
  /**
   * C++ original:
   * ```cpp
   * uint16_t rgba[4]
   * ```
   */
  public var rgba: IntArray,
)
