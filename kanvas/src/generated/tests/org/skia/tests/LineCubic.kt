package org.skia.tests

import org.skia.core.SkDLine

/**
 * C++ original:
 * ```cpp
 * struct lineCubic {
 *     CubicPts cubic;
 *     SkDLine line;
 * }
 * ```
 */
public data class LineCubic public constructor(
  /**
   * C++ original:
   * ```cpp
   * CubicPts cubic
   * ```
   */
  public var cubic: CubicPts,
  /**
   * C++ original:
   * ```cpp
   * SkDLine line
   * ```
   */
  public var line: SkDLine,
)
