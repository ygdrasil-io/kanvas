package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/bug12866.cpp`.
 * Regression test for Skia bug 12866 — quad path rendered as stroke and fill.
 * @see https://github.com/google/skia/blob/main/gm/bug12866.cpp
 */

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class Bug12866Gm : SkiaGm {
    override val name = "bug12866"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 64

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val strokePath = buildPath()
        val strokePaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 3f,
        )
        val fillPaint = Paint(antiAlias = true)

        canvas.save()
        canvas.translate(10f, 10f)
        canvas.drawPath(strokePath, strokePaint)
        canvas.restore()

        canvas.save()
        canvas.translate(74f, 10f)
        canvas.drawPath(strokePath, fillPaint)
        canvas.restore()
    }

    private fun buildPath(): Path = Path {
        moveTo(2100.92f, 115.991f)
        quadTo(2063.28f, 179.199f, 2063.28f, 159.058f)
        quadTo(2063.28f, 138.843f, 2073.27f, 127.417f)
        quadTo(2083.27f, 115.991f, 2100.92f, 115.991f)
        close()
    }.apply { fillType = FillType.WINDING }
}
