package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * struct cubicConic {
 *     CubicPts cubic;
 *     ConicPts conic;
 * }
 * ```
 */
public data class CubicConic public constructor(
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
   * ConicPts conic
   * ```
   */
  public var conic: ConicPts,
)
