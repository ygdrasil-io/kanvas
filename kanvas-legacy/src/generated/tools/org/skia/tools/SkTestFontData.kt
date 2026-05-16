package org.skia.tools

import kotlin.Int
import kotlin.String
import kotlin.UByte
import org.skia.core.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkUnichar
import org.skia.math.SkFixed
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkTestFontData {
 *     const SkScalar*      fPoints;
 *     const unsigned char* fVerbs;
 *     const SkUnichar*     fCharCodes;
 *     const size_t         fCharCodesCount;
 *     const SkFixed*       fWidths;
 *     const SkFontMetrics& fMetrics;
 *     const char*          fName;
 *     SkFontStyle          fStyle;
 * }
 * ```
 */
public data class SkTestFontData public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkScalar*      fPoints
   * ```
   */
  public val fPoints: SkScalar?,
  /**
   * C++ original:
   * ```cpp
   * const unsigned char* fVerbs
   * ```
   */
  public val fVerbs: UByte?,
  /**
   * C++ original:
   * ```cpp
   * const SkUnichar*     fCharCodes
   * ```
   */
  public val fCharCodes: SkUnichar?,
  /**
   * C++ original:
   * ```cpp
   * const size_t         fCharCodesCount
   * ```
   */
  public val fCharCodesCount: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkFixed*       fWidths
   * ```
   */
  public val fWidths: SkFixed?,
  /**
   * C++ original:
   * ```cpp
   * const SkFontMetrics& fMetrics
   * ```
   */
  public val fMetrics: SkFontMetrics,
  /**
   * C++ original:
   * ```cpp
   * const char*          fName
   * ```
   */
  public val fName: String?,
  /**
   * C++ original:
   * ```cpp
   * SkFontStyle          fStyle
   * ```
   */
  public var fStyle: SkFontStyle,
)
