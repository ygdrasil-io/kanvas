package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder for Skia's `gm/pdf_never_embed.cpp::pdf_table_based_subset`
 * (`DEF_SIMPLE_GM_CAN_FAIL`, 512 × 128).
 *
 * Upstream tests the PDF subsetter for OpenType fonts by loading
 * `fonts/SpiderSymbol.ttf`, `fonts/SpiderSymbol.woff`, and
 * `fonts/SpiderSymbol.woff2` six ways (resource-backed vs
 * stream-backed via `GetResourceAsStream(resource, useStream=true/false)`
 * through `SkFontMgr::TestFontMgr().makeFromStream(…)`), then renders
 * a spider symbol glyph (U+F021) for each typeface via the 7-argument
 * `canvas->drawGlyphs(glyphs, positions, clusters, utf8text, origin,
 * font, paint)` overload — the overload that carries cluster mapping
 * for PDF text extraction / subsetting.
 *
 * **Why INTRACTABLE:**
 *  1. `ToolUtils::TestFontMgr()` is not exposed in `:cpu-raster`'s
 *     [org.skia.tools.ToolUtils] — the Kotlin port only provides
 *     [org.skia.tools.ToolUtils.DefaultPortableTypeface] /
 *     [org.skia.tools.ToolUtils.CreateTypefaceFromResource] backed by
 *     AWT; a `TestFontMgr`-style registry that round-trips woff/woff2
 *     streams via FreeType is not yet wired up.
 *  2. The font resources (`SpiderSymbol.ttf/.woff/.woff2`) are not
 *     bundled in `:kanvas-skia`'s classpath.
 *  3. The 7-argument `drawGlyphs` (with cluster/utf8 overload for PDF
 *     subsetting) is not exposed on [org.skia.core.SkCanvas] — the
 *     existing port uses `SkTextBlobBuilder.allocRunPos` to emulate
 *     the simpler `drawGlyphs(glyphs, pos, origin, font, paint)` path
 *     but has no cluster-mapped overload.
 *
 * TODO: STUB.PDF_TABLE_SUBSET_FONTMGR — unblock by:
 *  (a) exposing `ToolUtils.TestFontMgr()` backed by a FreeType/JNI
 *      font manager that supports woff/woff2 streams, and
 *  (b) adding `SkCanvas.drawGlyphs(glyphs, positions, clusters,
 *      utf8Text, origin, font, paint)` to the Kotlin API surface.
 */
public class PdfTableBasedSubsetGM : GM() {

    override fun getName(): String = "pdf_table_based_subset"
    override fun getISize(): SkISize = SkISize.Make(512, 128)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.PDF_TABLE_SUBSET_FONTMGR: TestFontMgr/woff streams and cluster drawGlyphs overload not in :kanvas-skia")
    }
}
