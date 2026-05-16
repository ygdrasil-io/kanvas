package org.skia.tests

import org.skia.core.SkGlyph

/**
 * C++ original:
 * ```cpp
 * class SkGlyphTestPeer {
 * public:
 *     static void SetGlyph(SkGlyph* glyph) {
 *         // Tweak the bounds to make them unique based on glyph id.
 *         const SkGlyphID uniquify = glyph->getGlyphID();
 *         glyph->fAdvanceX = 10;
 *         glyph->fAdvanceY = 11;
 *         glyph->fLeft = -1 - uniquify;
 *         glyph->fTop = -2;
 *         glyph->fWidth = 8;
 *         glyph->fHeight = 9;
 *         glyph->fMaskFormat = SkMask::Format::kA8_Format;
 *     }
 * }
 * ```
 */
public open class SkGlyphTestPeer {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static void SetGlyph(SkGlyph* glyph) {
     *         // Tweak the bounds to make them unique based on glyph id.
     *         const SkGlyphID uniquify = glyph->getGlyphID();
     *         glyph->fAdvanceX = 10;
     *         glyph->fAdvanceY = 11;
     *         glyph->fLeft = -1 - uniquify;
     *         glyph->fTop = -2;
     *         glyph->fWidth = 8;
     *         glyph->fHeight = 9;
     *         glyph->fMaskFormat = SkMask::Format::kA8_Format;
     *     }
     * ```
     */
    public fun setGlyph(glyph: SkGlyph?) {
      TODO("Implement setGlyph")
    }
  }
}
