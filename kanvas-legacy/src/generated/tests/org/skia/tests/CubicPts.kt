package org.skia.tests

import kotlin.Array
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct CubicPts {
 *     static const int kPointCount = 4;
 *     SkDPoint fPts[kPointCount];
 * }
 * ```
 */
public data class CubicPts public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const int kPointCount = 4
   * ```
   */
  public var fPts: Array<SkPoint>,
) {
  public companion object {
    public val kPointCount: Int = TODO("Initialize kPointCount")
  }
}
