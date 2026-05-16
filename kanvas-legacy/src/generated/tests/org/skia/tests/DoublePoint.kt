package org.skia.tests

import kotlin.Double

/**
 * C++ original:
 * ```cpp
 * struct DoublePoint {
 *     double x;
 *     double y;
 * }
 * ```
 */
public data class DoublePoint public constructor(
  /**
   * C++ original:
   * ```cpp
   * double x
   * ```
   */
  public var x: Double,
  /**
   * C++ original:
   * ```cpp
   * double y
   * ```
   */
  public var y: Double,
)
