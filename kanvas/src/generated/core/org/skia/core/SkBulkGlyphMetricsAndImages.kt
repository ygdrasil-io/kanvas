package org.skia.core

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.memory.AutoSTArraykTypicalGlyphCount

/**
 * C++ original:
 * ```cpp
 * class SkBulkGlyphMetricsAndImages {
 * public:
 *     explicit SkBulkGlyphMetricsAndImages(const SkStrikeSpec& spec);
 *     explicit SkBulkGlyphMetricsAndImages(sk_sp<SkStrike>&& strike);
 *     ~SkBulkGlyphMetricsAndImages();
 *     SkSpan<const SkGlyph*> glyphs(SkSpan<const SkPackedGlyphID> packedIDs);
 *     const SkGlyph* glyph(SkPackedGlyphID packedID);
 *     const SkDescriptor& descriptor() const;
 *
 * private:
 *     inline static constexpr int kTypicalGlyphCount = 64;
 *     skia_private::AutoSTArray<kTypicalGlyphCount, const SkGlyph*> fGlyphs;
 *     sk_sp<SkStrike> fStrike;
 * }
 * ```
 */
public data class SkBulkGlyphMetricsAndImages public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kTypicalGlyphCount = 64
   * ```
   */
  private val fGlyphs: AutoSTArraykTypicalGlyphCount<SkGlyph?>,
  /**
   * C++ original:
   * ```cpp
   * skia_private::AutoSTArray<kTypicalGlyphCount, const SkGlyph*> fGlyphs
   * ```
   */
  private var fStrike: SkSp<SkStrike>,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSpan<const SkGlyph*> SkBulkGlyphMetricsAndImages::glyphs(SkSpan<const SkPackedGlyphID> glyphIDs) {
   *     fGlyphs.reset(glyphIDs.size());
   *     return fStrike->prepareImages(glyphIDs, fGlyphs.get());
   * }
   * ```
   */
  public fun glyphs(packedIDs: SkSpan<SkPackedGlyphID>): SkSpan<SkGlyph?> {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkGlyph* SkBulkGlyphMetricsAndImages::glyph(SkPackedGlyphID packedID) {
   *     return this->glyphs(SkSpan<const SkPackedGlyphID>{&packedID, 1})[0];
   * }
   * ```
   */
  public fun glyph(packedID: SkPackedGlyphID): SkGlyph {
    TODO("Implement glyph")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkDescriptor& SkBulkGlyphMetricsAndImages::descriptor() const {
   *     return fStrike->getDescriptor();
   * }
   * ```
   */
  public fun descriptor(): SkDescriptor {
    TODO("Implement descriptor")
  }

  public companion object {
    private val kTypicalGlyphCount: Int = TODO("Initialize kTypicalGlyphCount")
  }
}
