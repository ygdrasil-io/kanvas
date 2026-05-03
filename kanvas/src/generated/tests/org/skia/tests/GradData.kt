package org.skia.tests

import kotlin.Float
import kotlin.ULong
import org.skia.effects.SkGradient
import org.skia.foundation.SkTileMode
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct GradData {
 *     size_t           fCount;
 *     const SkColor4f* fColors;
 *     const float*     fPos;
 *
 *     SkGradient operator()(SkTileMode tm) const {
 *         SkSpan<const float> pos;
 *         if (fPos) {
 *             pos = {fPos, fCount};
 *         }
 *         return {{{fColors, fCount}, pos, tm}, {}};
 *     }
 * }
 * ```
 */
public data class GradData public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t           fCount
   * ```
   */
  public var fCount: ULong,
  /**
   * C++ original:
   * ```cpp
   * const SkColor4f* fColors
   * ```
   */
  public val fColors: SkColor4f?,
  /**
   * C++ original:
   * ```cpp
   * const float*     fPos
   * ```
   */
  public val fPos: Float?,
) {
  /**
   * C++ original:
   * ```cpp
   * SkGradient operator()(SkTileMode tm) const {
   *         SkSpan<const float> pos;
   *         if (fPos) {
   *             pos = {fPos, fCount};
   *         }
   *         return {{{fColors, fCount}, pos, tm}, {}};
   *     }
   * ```
   */
  public operator fun invoke(tm: SkTileMode): SkGradient {
    TODO("Implement invoke")
  }
}
