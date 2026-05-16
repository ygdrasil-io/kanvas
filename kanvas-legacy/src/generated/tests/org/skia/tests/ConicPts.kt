package org.skia.tests

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ConicPts {
 *     QuadPts fPts;
 *     SkScalar fWeight;
 * }
 * ```
 */
public data class ConicPts public constructor(
  /**
   * C++ original:
   * ```cpp
   * QuadPts fPts
   * ```
   */
  public var fPts: QuadPts,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWeight
   * ```
   */
  public var fWeight: Int,
)
