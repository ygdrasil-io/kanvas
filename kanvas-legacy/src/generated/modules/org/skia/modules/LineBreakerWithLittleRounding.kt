package org.skia.modules

import kotlin.Boolean
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct LineBreakerWithLittleRounding {
 *     LineBreakerWithLittleRounding(SkScalar maxWidth, bool applyRoundingHack)
 *         : fLower(maxWidth - 0.25f)
 *         , fMaxWidth(maxWidth)
 *         , fUpper(maxWidth + 0.25f)
 *         , fApplyRoundingHack(applyRoundingHack) {}
 *
 *     bool breakLine(SkScalar width) const {
 *         if (width < fLower) {
 *             return false;
 *         } else if (width > fUpper) {
 *             return true;
 *         }
 *
 *         auto val = std::fabs(width);
 *         SkScalar roundedWidth;
 *         if (fApplyRoundingHack) {
 *             if (val < 10000) {
 *                 roundedWidth = SkScalarRoundToScalar(width * 100) * (1.0f/100);
 *             } else if (val < 100000) {
 *                 roundedWidth = SkScalarRoundToScalar(width *  10) * (1.0f/10);
 *             } else {
 *                 roundedWidth = SkScalarFloorToScalar(width);
 *             }
 *         } else {
 *             if (val < 10000) {
 *                 roundedWidth = SkScalarFloorToScalar(width * 100) * (1.0f/100);
 *             } else if (val < 100000) {
 *                 roundedWidth = SkScalarFloorToScalar(width *  10) * (1.0f/10);
 *             } else {
 *                 roundedWidth = SkScalarFloorToScalar(width);
 *             }
 *         }
 *         return roundedWidth > fMaxWidth;
 *     }
 *
 *     const SkScalar fLower, fMaxWidth, fUpper;
 *     const bool fApplyRoundingHack;
 * }
 * ```
 */
public data class LineBreakerWithLittleRounding public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fLower
   * ```
   */
  public val fLower: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fLower, fMaxWidth
   * ```
   */
  public val fMaxWidth: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fLower, fMaxWidth, fUpper
   * ```
   */
  public val fUpper: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * const bool fApplyRoundingHack
   * ```
   */
  public val fApplyRoundingHack: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool breakLine(SkScalar width) const {
   *         if (width < fLower) {
   *             return false;
   *         } else if (width > fUpper) {
   *             return true;
   *         }
   *
   *         auto val = std::fabs(width);
   *         SkScalar roundedWidth;
   *         if (fApplyRoundingHack) {
   *             if (val < 10000) {
   *                 roundedWidth = SkScalarRoundToScalar(width * 100) * (1.0f/100);
   *             } else if (val < 100000) {
   *                 roundedWidth = SkScalarRoundToScalar(width *  10) * (1.0f/10);
   *             } else {
   *                 roundedWidth = SkScalarFloorToScalar(width);
   *             }
   *         } else {
   *             if (val < 10000) {
   *                 roundedWidth = SkScalarFloorToScalar(width * 100) * (1.0f/100);
   *             } else if (val < 100000) {
   *                 roundedWidth = SkScalarFloorToScalar(width *  10) * (1.0f/10);
   *             } else {
   *                 roundedWidth = SkScalarFloorToScalar(width);
   *             }
   *         }
   *         return roundedWidth > fMaxWidth;
   *     }
   * ```
   */
  public fun breakLine(width: SkScalar): Boolean {
    TODO("Implement breakLine")
  }
}
