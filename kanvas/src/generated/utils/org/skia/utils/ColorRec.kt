package org.skia.utils

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ColorRec {
 *     uint8_t     r, g, b;
 * }
 * ```
 */
public data class ColorRec public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t     r
   * ```
   */
  public var r: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t     r, g
   * ```
   */
  public var g: Int,
  /**
   * C++ original:
   * ```cpp
   * uint8_t     r, g, b
   * ```
   */
  public var b: Int,
)
