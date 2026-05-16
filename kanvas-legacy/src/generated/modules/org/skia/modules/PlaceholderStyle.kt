package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct PlaceholderStyle {
 *     PlaceholderStyle() = default;
 *     PlaceholderStyle(SkScalar width, SkScalar height, PlaceholderAlignment alignment,
 *                      TextBaseline baseline, SkScalar offset)
 *             : fWidth(width)
 *             , fHeight(height)
 *             , fAlignment(alignment)
 *             , fBaseline(baseline)
 *             , fBaselineOffset(offset) {}
 *
 *     bool equals(const PlaceholderStyle&) const;
 *
 *     SkScalar fWidth = 0;
 *     SkScalar fHeight = 0;
 *     PlaceholderAlignment fAlignment = PlaceholderAlignment::kBaseline;
 *     TextBaseline fBaseline = TextBaseline::kAlphabetic;
 *     // Distance from the top edge of the rect to the baseline position. This
 *     // baseline will be aligned against the alphabetic baseline of the surrounding
 *     // text.
 *     //
 *     // Positive values drop the baseline lower (positions the rect higher) and
 *     // small or negative values will cause the rect to be positioned underneath
 *     // the line. When baseline == height, the bottom edge of the rect will rest on
 *     // the alphabetic baseline.
 *     SkScalar fBaselineOffset = 0;
 * }
 * ```
 */
public data class PlaceholderStyle public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fWidth
   * ```
   */
  public var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fHeight
   * ```
   */
  public var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * PlaceholderAlignment fAlignment = PlaceholderAlignment::kBaseline
   * ```
   */
  public var fAlignment: PlaceholderAlignment,
  /**
   * C++ original:
   * ```cpp
   * TextBaseline fBaseline
   * ```
   */
  public var fBaseline: Int,
  /**
   * C++ original:
   * ```cpp
   * SkScalar fBaselineOffset
   * ```
   */
  public var fBaselineOffset: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool PlaceholderStyle::equals(const PlaceholderStyle& other) const {
   *     return nearlyEqual(fWidth, other.fWidth) &&
   *            nearlyEqual(fHeight, other.fHeight) &&
   *            fAlignment == other.fAlignment &&
   *            fBaseline == other.fBaseline &&
   *            (fAlignment != PlaceholderAlignment::kBaseline ||
   *             nearlyEqual(fBaselineOffset, other.fBaselineOffset));
   * }
   * ```
   */
  public override fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
