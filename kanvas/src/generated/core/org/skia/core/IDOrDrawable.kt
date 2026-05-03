package org.skia.core

import org.skia.foundation.SkGlyphID

/**
 * C++ original:
 * ```cpp
 * union IDOrDrawable {
 *     SkGlyphID fGlyphID;
 *     SkDrawable* fDrawable;
 * }
 * ```
 */
public data class IDOrDrawable public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkGlyphID fGlyphID
   * ```
   */
  private var fGlyphID: SkGlyphID,
  /**
   * C++ original:
   * ```cpp
   * SkDrawable* fDrawable
   * ```
   */
  private var fDrawable: SkDrawable?,
)
