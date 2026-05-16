package org.skia.tools

import kotlin.String
import org.skia.foundation.SkUnichar
import org.skia.math.SkPoint
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct SkSVGTestTypefaceGlyphData {
 *     const char* fSvgResourcePath;
 *     SkPoint     fOrigin;  // y-down
 *     SkScalar    fAdvance;
 *     SkUnichar   fUnicode;  // TODO: this limits to 1:1
 * }
 * ```
 */
public data class SkSVGTestTypefaceGlyphData public constructor(
  /**
   * C++ original:
   * ```cpp
   * const char* fSvgResourcePath
   * ```
   */
  public val fSvgResourcePath: String?,
  /**
   * C++ original:
   * ```cpp
   * SkPoint     fOrigin
   * ```
   */
  public var fOrigin: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * SkScalar    fAdvance
   * ```
   */
  public var fAdvance: SkScalar,
  /**
   * C++ original:
   * ```cpp
   * SkUnichar   fUnicode
   * ```
   */
  public var fUnicode: SkUnichar,
)
