package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkGlyphID
import org.skia.math.SkPoint
import org.skia.math.SkVector

/**
 * C++ original:
 * ```cpp
 * struct ShapedGlyph {
 *     SkGlyphID fID;
 *     uint32_t fCluster;
 *     SkPoint fOffset;
 *     SkVector fAdvance;
 *     bool fMayLineBreakBefore;
 *     bool fMustLineBreakBefore;
 *     bool fHasVisual;
 *     bool fGraphemeBreakBefore;
 *     bool fUnsafeToBreak;
 * }
 * ```
 */
public data class ShapedGlyph public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkGlyphID fID
   * ```
   */
  public var fID: SkGlyphID,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fCluster
   * ```
   */
  public var fCluster: Int,
  /**
   * C++ original:
   * ```cpp
   * SkPoint fOffset
   * ```
   */
  public var fOffset: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkVector fAdvance
   * ```
   */
  public var fAdvance: SkVector,
  /**
   * C++ original:
   * ```cpp
   * bool fMayLineBreakBefore
   * ```
   */
  public var fMayLineBreakBefore: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fMustLineBreakBefore
   * ```
   */
  public var fMustLineBreakBefore: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasVisual
   * ```
   */
  public var fHasVisual: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fGraphemeBreakBefore
   * ```
   */
  public var fGraphemeBreakBefore: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fUnsafeToBreak
   * ```
   */
  public var fUnsafeToBreak: Boolean,
)
