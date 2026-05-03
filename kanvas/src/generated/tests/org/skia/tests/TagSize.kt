package org.skia.tests

import kotlin.Int
import org.skia.foundation.SkFontTableTag

/**
 * C++ original:
 * ```cpp
 * struct TagSize {
 *     SkFontTableTag  fTag;
 *     size_t          fSize;
 * }
 * ```
 */
public data class TagSize public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkFontTableTag  fTag
   * ```
   */
  public var fTag: SkFontTableTag,
  /**
   * C++ original:
   * ```cpp
   * size_t          fSize
   * ```
   */
  public var fSize: Int,
)
