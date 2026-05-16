package org.skia.tools

import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * struct SubFont {
 *     const char* fFamilyName;
 *     const char* fStyleName;
 *     SkFontStyle fStyle;
 *     SkTestFontData& fFont;
 *     const char* fFile;
 * }
 * ```
 */
public data class SubFont public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fFamilyName
   * ```
   */
  public val fFamilyName: String?,
  /**
   * C++ original:
   * ```cpp
   * const char* fStyleName
   * ```
   */
  public val fStyleName: String?,
  /**
   * C++ original:
   * ```cpp
   * SkFontStyle fStyle
   * ```
   */
  public var fStyle: Int,
  /**
   * C++ original:
   * ```cpp
   * SkTestFontData& fFont
   * ```
   */
  public var fFont: Int,
  /**
   * C++ original:
   * ```cpp
   * const char* fFile
   * ```
   */
  public val fFile: String?,
)
