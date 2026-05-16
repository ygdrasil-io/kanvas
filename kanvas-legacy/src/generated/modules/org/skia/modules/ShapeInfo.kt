package org.skia.modules

import kotlin.Float
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct ShapeInfo {
 *    SkVector ctrl0,
 *             ctrl1;
 *    float    e0, e1, crs;
 * }
 * ```
 */
public data class ShapeInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkVector ctrl0
   * ```
   */
  public var ctrl0: SkVector,
  /**
   * C++ original:
   * ```cpp
   * SkVector ctrl0,
   *             ctrl1
   * ```
   */
  public var ctrl1: SkVector,
  /**
   * C++ original:
   * ```cpp
   * float    e0
   * ```
   */
  public var e0: Float,
  /**
   * C++ original:
   * ```cpp
   * float    e0, e1
   * ```
   */
  public var e1: Float,
  /**
   * C++ original:
   * ```cpp
   * float    e0, e1, crs
   * ```
   */
  public var crs: Float,
)
