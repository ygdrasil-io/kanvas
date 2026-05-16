package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct RGB { F r, g, b; }
 * ```
 */
public data class RGB public constructor(
  /**
   * C++ original:
   * ```cpp
   * F r
   * ```
   */
  public var r: Int,
  /**
   * C++ original:
   * ```cpp
   * F r, g
   * ```
   */
  public var g: Int,
  /**
   * C++ original:
   * ```cpp
   * F r, g, b
   * ```
   */
  public var b: Int,
)
