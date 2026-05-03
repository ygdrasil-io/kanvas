package org.skia.tests

import kotlin.Boolean
import kotlin.Double

/**
 * C++ original:
 * ```cpp
 * struct TRange {
 *     double tMin1;
 *     double tMin2;
 *     double t1;
 *     double t2;
 *     double tMin;
 *     double a1;
 *     double a2;
 *     bool ccw;
 * }
 * ```
 */
public data class TRange public constructor(
  /**
   * C++ original:
   * ```cpp
   * double tMin1
   * ```
   */
  public var tMin1: Double,
  /**
   * C++ original:
   * ```cpp
   * double tMin2
   * ```
   */
  public var tMin2: Double,
  /**
   * C++ original:
   * ```cpp
   * double t1
   * ```
   */
  public var t1: Double,
  /**
   * C++ original:
   * ```cpp
   * double t2
   * ```
   */
  public var t2: Double,
  /**
   * C++ original:
   * ```cpp
   * double tMin
   * ```
   */
  public var tMin: Double,
  /**
   * C++ original:
   * ```cpp
   * double a1
   * ```
   */
  public var a1: Double,
  /**
   * C++ original:
   * ```cpp
   * double a2
   * ```
   */
  public var a2: Double,
  /**
   * C++ original:
   * ```cpp
   * bool ccw
   * ```
   */
  public var ccw: Boolean,
)
