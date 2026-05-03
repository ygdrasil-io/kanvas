package org.skia.tests

import kotlin.Int
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct Expectation {
 *     int fX;
 *     int fY;
 *     SkColor4f fColor;
 * }
 * ```
 */
public data class Expectation public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fX
   * ```
   */
  public var fX: Int,
  /**
   * C++ original:
   * ```cpp
   * int fY
   * ```
   */
  public var fY: Int,
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fColor
   * ```
   */
  public var fColor: SkColor4f,
)
