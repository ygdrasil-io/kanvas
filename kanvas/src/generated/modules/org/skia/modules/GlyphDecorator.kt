package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.ULong
import org.skia.core.SkCanvas
import org.skia.foundation.SkRefCnt

/**
 * C++ original:
 * ```cpp
 * class GlyphDecorator : public SkRefCnt {
 * public:
 *     struct GlyphInfo {
 *         SkRect   fBounds;  // visual glyph bounds
 *         SkMatrix fMatrix;  // glyph matrix
 *         size_t   fCluster; // cluster index in the original text string
 *         float    fAdvance; // horizontal glyph advance
 *     };
 *
 *     struct TextInfo {
 *         SkSpan<const GlyphInfo> fGlyphs;
 *         float                   fScale;  // Additional font scale applied by auto-sizing.
 *     };
 *
 *     virtual void onDecorate(SkCanvas*, const TextInfo&) = 0;
 * }
 * ```
 */
public abstract class GlyphDecorator : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * virtual void onDecorate(SkCanvas*, const TextInfo&) = 0
   * ```
   */
  public abstract fun onDecorate(param0: SkCanvas?, param1: TextInfo)

  public data class GlyphInfo public constructor(
    public var fBounds: Int,
    public var fMatrix: Int,
    public var fCluster: ULong,
    public var fAdvance: Float,
  )

  public data class TextInfo public constructor(
    public var fGlyphs: Int,
    public var fScale: Float,
  )
}
