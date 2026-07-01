package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/trickycubicstrokes.cpp::trickycubicstrokes_largeradius`.
 * Two cubic paths with large stroke width exposing cusp-circle artifacts.
 * @see https://github.com/google/skia/blob/main/gm/trickycubicstrokes.cpp
 */
class TrickyCubicStrokesLargeRadiusGm : SkiaGm {
    override val name = "trickycubicstrokes_largeradius"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 68.9
    override val width = 128
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            for (y in 0..1) {
                val shift = 210f * y
                val dy = 5f * y
                moveTo(159.429f, 149.808f + shift)
                cubicTo(
                    232.5f, 149.808f + dy + shift,
                    232.5f, 149.808f + dy + shift,
                    305.572f, 149.808f + shift,
                )
            }
        }
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 200f,
            antiAlias = true,
        )
        canvas.scale(0.5f, 0.5f)
        canvas.translate(-125f, 0f)
        canvas.drawPath(path, paint)
    }
}
