package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class Contour {
 * public:
 *     SkSpan<const Point> points;
 *     SkIRect bounds;
 * }
 * ```
 */
public data class Contour public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const Point> points
   * ```
   */
  public var points: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect bounds
   * ```
   */
  public var bounds: Int,
)
