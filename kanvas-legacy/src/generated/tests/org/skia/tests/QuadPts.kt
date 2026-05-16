package org.skia.tests

import kotlin.Array
import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct QuadPts {
 *     static const int kPointCount = 3;
 *     SkDPoint fPts[kPointCount];
 * }
 * ```
 */
public data class QuadPts public constructor(
  /**
   * C++ original:
   * ```cpp
   * static const int kPointCount = 3
   * ```
   */
  public var fPts: Array<SkPoint>,
) {
  public companion object {
    public val kPointCount: Int = TODO("Initialize kPointCount")
  }
}
