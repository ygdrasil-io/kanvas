package org.skia.core

import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkPath

/**
 * C++ original:
 * ```cpp
 * union IDOrPath {
 *     IDOrPath() {}
 *     IDOrPath(SkGlyphID glyphID) : fGlyphID{glyphID} {}
 *
 *     // PathOpSubmitter takes care of destroying the paths.
 *     ~IDOrPath() {}
 *     SkGlyphID fGlyphID;
 *     SkPath fPath;
 * }
 * ```
 */
public data class IDOrPath public constructor(
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
   * SkPath fPath
   * ```
   */
  private var fPath: SkPath,
)
