package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Curve {
 *     int ptCount;
 *     CubicPts curve;  // largest can hold lines / quads/ cubics
 * }
 * ```
 */
public data class Curve public constructor(
  /**
   * C++ original:
   * ```cpp
   * int ptCount
   * ```
   */
  public var ptCount: Int,
  /**
   * C++ original:
   * ```cpp
   * CubicPts curve
   * ```
   */
  public var curve: CubicPts,
)
