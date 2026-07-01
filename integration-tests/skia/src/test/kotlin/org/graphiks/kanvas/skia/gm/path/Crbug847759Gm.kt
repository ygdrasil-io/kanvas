package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_847759.cpp` (500 x 500).
 * Hairline stroke of a closed squashed-oval-like path with tight miter.
 * @see https://github.com/google/skia/blob/main/gm/crbug_847759.cpp
 */
class Crbug847759Gm : SkiaGm {
    override val name = "crbug_847759"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(97f, 374.5f)
            cubicTo(97f, 359.8644528f, 155.8745488f, 348f, 228.5f, 348f)
            cubicTo(301.1254512f, 348f, 360f, 359.8644528f, 360f, 374.5f)
            cubicTo(360f, 389.1355472f, 301.1254512f, 401f, 228.5f, 401f)
            cubicTo(155.8745488f, 401f, 97f, 389.1355472f, 97f, 374.5f)
            close()
        }
        val paint = Paint(
            antiAlias = true,
            strokeWidth = 0f,
            strokeMiter = 1.5f,
            style = PaintStyle.STROKE,
        )
        canvas.translate(-80f, -330f)
        canvas.drawPath(path, paint)
    }
}
