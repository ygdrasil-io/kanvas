package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/pdf_never_embed.cpp::pdf_table_based_subset` (512 × 128).
 * Stub — requires TestFontMgr, woff/woff2 stream support, and cluster-aware drawGlyphs.
 * @see https://github.com/google/skia/blob/main/gm/pdf_never_embed.cpp
 */
class PdfTableBasedSubsetGm : SkiaGm {
    override val name = "pdf_table_based_subset"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 128
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* stub: requires TestFontMgr + cluster drawGlyphs overload */
    }
}
