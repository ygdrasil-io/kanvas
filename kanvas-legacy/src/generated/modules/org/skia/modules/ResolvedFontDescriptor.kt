package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ResolvedFontDescriptor {
 *     ResolvedFontDescriptor(TextIndex index, SkFont font)
 *             : fFont(std::move(font)), fTextStart(index) {}
 *     SkFont fFont;
 *     TextIndex fTextStart;
 * }
 * ```
 */
public data class ResolvedFontDescriptor public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkFont fFont
   * ```
   */
  public var fFont: Int,
  /**
   * C++ original:
   * ```cpp
   * TextIndex fTextStart
   * ```
   */
  public var fTextStart: Int,
)
