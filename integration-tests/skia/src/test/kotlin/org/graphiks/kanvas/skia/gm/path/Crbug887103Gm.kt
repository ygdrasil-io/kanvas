package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_887103.cpp` (520 x 520).
 * Three coincident triangles formed entirely of moveTo + lineTo.
 * @see https://github.com/google/skia/blob/main/gm/crbug_887103.cpp
 */
class Crbug887103Gm : SkiaGm {
    override val name = "crbug_887103"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 93.0
    override val width = 520
    override val height = 520

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)
        val path = Path {
            moveTo(510f, 20f)
            lineTo(500f, 20f)
            lineTo(510f, 500f)
            moveTo(500f, 20f)
            lineTo(510f, 500f)
            lineTo(500f, 510f)
            moveTo(500f, 30f)
            lineTo(510f, 10f)
            lineTo(10f, 30f)
        }
        canvas.drawPath(path, paint)
    }
}
