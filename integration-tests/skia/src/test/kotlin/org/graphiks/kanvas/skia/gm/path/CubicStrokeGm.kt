package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/strokes.cpp` (DEF_SIMPLE_GM CubicStroke).
 * Three near-identical stroked cubics with sub-1% stroke width differences.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class CubicStrokeGm : SkiaGm {
    override val name = "CubicStroke"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 13.7
    override val width = 384
    override val height = 384

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(-6000f, -6000f)
            cubicTo(-3500f, 5500f, -500f, 5500f, 2500f, -6500f)
        }
        var paint = Paint(style = PaintStyle.STROKE, strokeWidth = 1.0720f, antiAlias = true)
        canvas.drawPath(path, paint)
        paint = paint.copy(strokeWidth = 1.0721f)
        canvas.translate(10f, 10f)
        canvas.drawPath(path, paint)
        paint = paint.copy(strokeWidth = 1.0722f)
        canvas.translate(10f, 10f)
        canvas.drawPath(path, paint)
    }
}
