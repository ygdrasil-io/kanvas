package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong
import org.skia.foundation.SkColor
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSpan

public typealias SkGraphicsOpenTypeSVGDecoderFactory = SkOpenTypeSVGDecoder?

/**
 * C++ original:
 * ```cpp
 * class SkOpenTypeSVGDecoder {
 * public:
 *     /** Each instance probably owns an SVG DOM.
 *      *  The instance may be cached so needs to report how much memory it retains.
 *      */
 *     virtual size_t approximateSize() = 0;
 *     virtual bool render(SkCanvas&, int upem, SkGlyphID glyphId,
 *                         SkColor foregroundColor, SkSpan<SkColor> palette) = 0;
 *     virtual ~SkOpenTypeSVGDecoder() = default;
 * }
 * ```
 */
public abstract class SkOpenTypeSVGDecoder {
  /**
   * C++ original:
   * ```cpp
   * virtual size_t approximateSize() = 0
   * ```
   */
  public abstract fun approximateSize(): ULong

  /**
   * C++ original:
   * ```cpp
   * virtual bool render(SkCanvas&, int upem, SkGlyphID glyphId,
   *                         SkColor foregroundColor, SkSpan<SkColor> palette) = 0
   * ```
   */
  public abstract fun render(
    param0: SkCanvas,
    upem: Int,
    glyphId: SkGlyphID,
    foregroundColor: SkColor,
    palette: SkSpan<SkColor>,
  ): Boolean
}
