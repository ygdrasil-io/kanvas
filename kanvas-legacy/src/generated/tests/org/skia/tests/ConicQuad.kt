package org.skia.tests

/**
 * C++ original:
 * ```cpp
 * struct conicQuad {
 *     ConicPts conic;
 *     QuadPts quad;
 * }
 * ```
 */
public data class ConicQuad public constructor(
  /**
   * C++ original:
   * ```cpp
   * ConicPts conic
   * ```
   */
  public var conic: ConicPts,
  /**
   * C++ original:
   * ```cpp
   * QuadPts quad
   * ```
   */
  public var quad: QuadPts,
)
