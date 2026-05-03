package org.skia.core

import kotlin.Double

/**
 * C++ original:
 * ```cpp
 * struct DPoint {
 *     DPoint(double x_, double y_) : x{x_}, y{y_} {}
 *     DPoint(SkPoint p) : x{p.fX}, y{p.fY} {}
 *     double x, y;
 * }
 * ```
 */
public data class DPoint public constructor(
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
   * double x, y
   * ```
   */
  public var y: Double,
)
