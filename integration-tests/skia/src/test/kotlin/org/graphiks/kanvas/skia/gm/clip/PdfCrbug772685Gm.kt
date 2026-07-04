package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/pdf_never_embed.cpp::pdf_crbug_772685` (612 x 792).
 * Regression test for https://crbug.com/772685.
 * @see https://github.com/google/skia/blob/main/gm/pdf_never_embed.cpp
 */
class PdfCrbug772685Gm : SkiaGm {
    override val name = "pdf_crbug_772685"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 612
    override val height = 792

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.clipRect(Rect.fromLTRB(-1f, -1f, 613f, 793f))
        canvas.translate(-571f, 0f)
        canvas.scale(0.75f, 0.75f)
        canvas.clipRect(Rect.fromLTRB(-1f, -1f, 613f, 793f))
        canvas.translate(0f, -816f)
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 1224f, 1500f), Paint())
    }
}
