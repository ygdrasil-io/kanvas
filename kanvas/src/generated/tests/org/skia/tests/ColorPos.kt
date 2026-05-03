package org.skia.tests

import kotlin.Array
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.ULong
import org.skia.effects.SkGradient
import org.skia.foundation.SkColor
import org.skia.foundation.SkTileMode
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * struct ColorPos {
 *     SkColor4f* fColors;
 *     float*     fPos;
 *     size_t     fCount;
 *
 *     ColorPos() : fColors(nullptr), fPos(nullptr), fCount(0) {}
 *     ~ColorPos() {
 *         delete[] fColors;
 *         delete[] fPos;
 *     }
 *
 *     void construct(const SkColor colors[], const float pos[], int count) {
 *         fColors = new SkColor4f[count];
 *         std::transform(colors, colors + count, fColors, SkColor4f::FromColor);
 *         if (pos) {
 *             fPos = new float[count];
 *             std::copy(pos, pos + count, fPos);
 *             fPos[0] = 0;
 *             fPos[count - 1] = 1;
 *         }
 *         fCount = count;
 *     }
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
public data class ColorPos public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkColor4f* fColors
   * ```
   */
  public var fColors: SkColor4f?,
  /**
   * C++ original:
   * ```cpp
   * float*     fPos
   * ```
   */
  public var fPos: Float?,
  /**
   * C++ original:
   * ```cpp
   * size_t     fCount
   * ```
   */
  public var fCount: ULong,
) {
  /**
   * C++ original:
   * ```cpp
   * void construct(const SkColor colors[], const float pos[], int count) {
   *         fColors = new SkColor4f[count];
   *         std::transform(colors, colors + count, fColors, SkColor4f::FromColor);
   *         if (pos) {
   *             fPos = new float[count];
   *             std::copy(pos, pos + count, fPos);
   *             fPos[0] = 0;
   *             fPos[count - 1] = 1;
   *         }
   *         fCount = count;
   *     }
   * ```
   */
  public fun construct(
    colors: Array<SkColor>,
    pos: FloatArray,
    count: Int,
  ) {
    TODO("Implement construct")
  }

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
