package org.skia.core

import kotlin.Array
import kotlin.Int
import org.skia.foundation.SkGlyphID
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.math.SkScalar
import org.skia.memory.AutoSTArraykTypicalGlyphCount

/**
 * C++ original:
 * ```cpp
 * class SkBulkGlyphMetricsAndPaths {
 * public:
 *     explicit SkBulkGlyphMetricsAndPaths(const SkStrikeSpec& spec);
 *     explicit SkBulkGlyphMetricsAndPaths(sk_sp<SkStrike>&& strike);
 *     ~SkBulkGlyphMetricsAndPaths();
 *     SkSpan<const SkGlyph*> glyphs(SkSpan<const SkGlyphID> glyphIDs);
 *     const SkGlyph* glyph(SkGlyphID glyphID);
 *     void findIntercepts(const SkScalar bounds[2], SkScalar scale, SkScalar xPos,
 *                         const SkGlyph* glyph, SkScalar* array, int* count);
 *
 * private:
 *     inline static constexpr int kTypicalGlyphCount = 20;
 *     skia_private::AutoSTArray<kTypicalGlyphCount, const SkGlyph*> fGlyphs;
 *     sk_sp<SkStrike> fStrike;
 * }
 * ```
 */
public data class SkBulkGlyphMetricsAndPaths public constructor(
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
   * SkSpan<const SkGlyph*> SkBulkGlyphMetricsAndPaths::glyphs(SkSpan<const SkGlyphID> glyphIDs) {
   *     fGlyphs.reset(glyphIDs.size());
   *     return fStrike->preparePaths(glyphIDs, fGlyphs.get());
   * }
   * ```
   */
  public fun glyphs(glyphIDs: SkSpan<SkGlyphID>): SkSpan<SkGlyph?> {
    TODO("Implement glyphs")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkGlyph* SkBulkGlyphMetricsAndPaths::glyph(SkGlyphID glyphID) {
   *     return this->glyphs(SkSpan<const SkGlyphID>{&glyphID, 1})[0];
   * }
   * ```
   */
  public fun glyph(glyphID: SkGlyphID): SkGlyph {
    TODO("Implement glyph")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBulkGlyphMetricsAndPaths::findIntercepts(
   *     const SkScalar* bounds, SkScalar scale, SkScalar xPos,
   *     const SkGlyph* glyph, SkScalar* array, int* count) {
   *     // TODO(herb): remove this abominable const_cast. Do the intercepts really need to be on the
   *     //  glyph?
   *     fStrike->findIntercepts(bounds, scale, xPos, const_cast<SkGlyph*>(glyph), array, count);
   * }
   * ```
   */
  public fun findIntercepts(
    bounds: Array<SkScalar>,
    scale: SkScalar,
    xPos: SkScalar,
    glyph: SkGlyph?,
    array: SkScalar?,
    count: Int?,
  ) {
    TODO("Implement findIntercepts")
  }

  public companion object {
    private val kTypicalGlyphCount: Int = TODO("Initialize kTypicalGlyphCount")
  }
}
