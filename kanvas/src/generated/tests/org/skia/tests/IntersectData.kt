package org.skia.tests

import kotlin.Array
import kotlin.Double
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct IntersectData {
 *     const CubicPts fPts;
 *     const int fPtCount;
 *     double fTStart;
 *     double fTEnd;
 *     SkPoint fShortPts[4];
 * }
 * ```
 */
public data class IntersectData public constructor(
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
   * double fTStart
   * ```
   */
  public var fTStart: Double,
  /**
   * C++ original:
   * ```cpp
   * double fTEnd
   * ```
   */
  public var fTEnd: Double,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fShortPts[4]
   * ```
   */
  public var fShortPts: Array<SkPoint>,
)
