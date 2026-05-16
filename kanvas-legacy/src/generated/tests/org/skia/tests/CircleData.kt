package org.skia.tests

import kotlin.Array
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct CircleData {
 *     const CubicPts fPts;
 *     const int fPtCount;
 *     SkPoint fShortPts[4];
 * }
 * ```
 */
public data class CircleData public constructor(
  /**
   * C++ original:
   * ```cpp
   * const CubicPts fPts
   * ```
   */
  public val fPts: CubicPts,
  /**
   * C++ original:
   * ```cpp
   * const int fPtCount
   * ```
   */
  public val fPtCount: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fShortPts[4]
   * ```
   */
  public var fShortPts: Array<SkPoint>,
)
