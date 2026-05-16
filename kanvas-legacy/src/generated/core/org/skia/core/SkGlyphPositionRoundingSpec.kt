package org.skia.core

import kotlin.Boolean
import org.skia.math.SkIPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct SkGlyphPositionRoundingSpec {
 *     SkGlyphPositionRoundingSpec(bool isSubpixel, SkAxisAlignment axisAlignment);
 *     const SkVector halfAxisSampleFreq;
 *     const SkIPoint ignorePositionMask;
 *     const SkIPoint ignorePositionFieldMask;
 *
 * private:
 *     static SkVector HalfAxisSampleFreq(bool isSubpixel, SkAxisAlignment axisAlignment);
 *     static SkIPoint IgnorePositionMask(bool isSubpixel, SkAxisAlignment axisAlignment);
 *     static SkIPoint IgnorePositionFieldMask(bool isSubpixel, SkAxisAlignment axisAlignment);
 * }
 * ```
 */
public data class SkGlyphPositionRoundingSpec public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkVector halfAxisSampleFreq
   * ```
   */
  public val halfAxisSampleFreq: SkVector,
  /**
   * C++ original:
   * ```cpp
   * const SkIPoint ignorePositionMask
   * ```
   */
  public val ignorePositionMask: SkIPoint,
  /**
   * C++ original:
   * ```cpp
   * const SkIPoint ignorePositionFieldMask
   * ```
   */
  public val ignorePositionFieldMask: SkIPoint,
) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkVector SkGlyphPositionRoundingSpec::HalfAxisSampleFreq(
     *         bool isSubpixel, SkAxisAlignment axisAlignment) {
     *     if (!isSubpixel) {
     *         return {SK_ScalarHalf, SK_ScalarHalf};
     *     } else {
     *         switch (axisAlignment) {
     *             case SkAxisAlignment::kX:
     *                 return {SkPackedGlyphID::kSubpixelRound, SK_ScalarHalf};
     *             case SkAxisAlignment::kY:
     *                 return {SK_ScalarHalf, SkPackedGlyphID::kSubpixelRound};
     *             case SkAxisAlignment::kNone:
     *                 return {SkPackedGlyphID::kSubpixelRound, SkPackedGlyphID::kSubpixelRound};
     *         }
     *     }
     *
     *     // Some compilers need this.
     *     return {0, 0};
     * }
     * ```
     */
    private fun halfAxisSampleFreq(isSubpixel: Boolean, axisAlignment: SkAxisAlignment): SkVector {
      TODO("Implement halfAxisSampleFreq")
    }

    /**
     * C++ original:
     * ```cpp
     * SkIPoint SkGlyphPositionRoundingSpec::IgnorePositionMask(
     *         bool isSubpixel, SkAxisAlignment axisAlignment) {
     *     return SkIPoint::Make((!isSubpixel || axisAlignment == SkAxisAlignment::kY) ? 0 : ~0,
     *                           (!isSubpixel || axisAlignment == SkAxisAlignment::kX) ? 0 : ~0);
     * }
     * ```
     */
    private fun ignorePositionMask(isSubpixel: Boolean, axisAlignment: SkAxisAlignment): SkIPoint {
      TODO("Implement ignorePositionMask")
    }

    /**
     * C++ original:
     * ```cpp
     * SkIPoint SkGlyphPositionRoundingSpec::IgnorePositionFieldMask(bool isSubpixel,
     *                                                               SkAxisAlignment axisAlignment) {
     *     SkIPoint ignoreMask = IgnorePositionMask(isSubpixel, axisAlignment);
     *     SkIPoint answer{ignoreMask.x() & SkPackedGlyphID::kXYFieldMask.x(),
     *                     ignoreMask.y() & SkPackedGlyphID::kXYFieldMask.y()};
     *     return answer;
     * }
     * ```
     */
    private fun ignorePositionFieldMask(isSubpixel: Boolean, axisAlignment: SkAxisAlignment): SkIPoint {
      TODO("Implement ignorePositionFieldMask")
    }
  }
}
