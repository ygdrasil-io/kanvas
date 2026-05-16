package org.skia.tests

import kotlin.Array
import kotlin.Float
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct TrickyCubic {
 *     SkPoint fPoints[4];
 *     int fNumPts;
 *     CellFillMode fFillMode;
 *     float fScale = 1;
 * }
 * ```
 */
public data class TrickyCubic public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPoint fPoints[4]
   * ```
   */
  public var fPoints: Array<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * int fNumPts
   * ```
   */
  public var fNumPts: Int,
  /**
   * C++ original:
   * ```cpp
   * CellFillMode fFillMode
   * ```
   */
  public var fFillMode: CellFillMode,
  /**
   * C++ original:
   * ```cpp
   * float fScale = 1
   * ```
   */
  public var fScale: Float,
)
