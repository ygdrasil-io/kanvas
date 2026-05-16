package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * struct quadCubic {
 *     CubicPts cubic;
 *     QuadPts quad;
 * }
 * ```
 */
public data class QuadCubic public constructor(
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
   * QuadPts quad
   * ```
   */
  public var quad: QuadPts,
)
