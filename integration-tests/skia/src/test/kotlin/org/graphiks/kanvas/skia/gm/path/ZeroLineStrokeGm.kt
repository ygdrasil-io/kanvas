package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/zerolinestroke.cpp`.
 * Tests rendering of zero-length line strokes with round caps.
 * @see https://github.com/google/skia/blob/main/gm/zerolinestroke.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class ZeroLineStrokeGm : SkiaGm {
    override val name = "zerolinestroke"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 90
    override val height = 120

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 20f,
            antiAlias = true,
            strokeCap = StrokeCap.ROUND,
        )

        canvas.drawPath(
            Path {
                moveTo(30f, 90f); lineTo(30f, 90f)
                lineTo(60f, 90f); lineTo(60f, 90f)
            },
            paint,
        )

        canvas.drawPath(
            Path { moveTo(30f, 30f); lineTo(60f, 30f) },
            paint,
        )

        canvas.drawPath(
            Path {
                moveTo(30f, 60f); lineTo(30f, 60f)
                lineTo(60f, 60f)
            },
            paint,
        )
    }
}
