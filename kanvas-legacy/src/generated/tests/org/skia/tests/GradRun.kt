package org.skia.tests

import kotlin.Array
import kotlin.ULong
import org.skia.effects.SkGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkScalar
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct GradRun {
 *     SkColor4f fColors[4];
 *     SkScalar  fPos[4];
 *     size_t    fCount;
 *
 *     SkGradient grad(SkTileMode tm) const {
 *         return {{{fColors, fCount}, {fPos, fCount}, tm}, {}};
 *     }
 * }
 * ```
 */
public data class GradRun public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkColor4f fColors[4]
   * ```
   */
  public var fColors: Array<SkColor4f>,
  /**
   * C++ original:
   * ```cpp
   * SkScalar  fPos[4]
   * ```
   */
  public var fPos: Array<SkScalar>,
  /**
   * C++ original:
   * ```cpp
   * size_t    fCount
   * ```
   */
  public var fCount: ULong,
) {
  /**
   * C++ original:
   * ```cpp
   * SkGradient grad(SkTileMode tm) const {
   *         return {{{fColors, fCount}, {fPos, fCount}, tm}, {}};
   *     }
   * ```
   */
  public fun grad(tm: SkTileMode): SkGradient {
    TODO("Implement grad")
  }
}
