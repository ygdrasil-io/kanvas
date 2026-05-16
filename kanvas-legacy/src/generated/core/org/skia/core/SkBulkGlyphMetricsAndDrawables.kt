package org.skia.core

import kotlin.Int
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.memory.AutoSTArraykTypicalGlyphCount

/**
 * C++ original:
 * ```cpp
 * class SkBulkGlyphMetricsAndDrawables {
 * public:
 *     explicit SkBulkGlyphMetricsAndDrawables(const SkStrikeSpec& spec);
 *     explicit SkBulkGlyphMetricsAndDrawables(sk_sp<SkStrike>&& strike);
 *     ~SkBulkGlyphMetricsAndDrawables();
 *     SkSpan<const SkGlyph*> glyphs(SkSpan<const SkGlyphID> glyphIDs);
 *     const SkGlyph* glyph(SkGlyphID glyphID);
 *
 * private:
 *     inline static constexpr int kTypicalGlyphCount = 20;
 *     skia_private::AutoSTArray<kTypicalGlyphCount, const SkGlyph*> fGlyphs;
 *     sk_sp<SkStrike> fStrike;
 * }
 * ```
 */
public data class SkBulkGlyphMetricsAndDrawables public constructor(
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kTypicalGlyphCount = 20
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
   * SkSpan<const SkGlyph*> SkBulkGlyphMetricsAndDrawables::glyphs(SkSpan<const SkGlyphID> glyphIDs) {
   *     fGlyphs.reset(glyphIDs.size());
   *     return fStrike->prepareDrawables(glyphIDs, fGlyphs.get());
   * }
   * ```
   */
  public fun glyphs(glyphIDs: SkSpan<SkGlyphID>): SkSpan<SkGlyph?> {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkGlyph* SkBulkGlyphMetricsAndDrawables::glyph(SkGlyphID glyphID) {
   *     return this->glyphs(SkSpan<const SkGlyphID>{&glyphID, 1})[0];
   * }
   * ```
   */
  public fun glyph(glyphID: SkGlyphID): SkGlyph {
    TODO("Implement glyph")
  }

  public companion object {
    private val kTypicalGlyphCount: Int = TODO("Initialize kTypicalGlyphCount")
  }
}
