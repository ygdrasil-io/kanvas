package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_908646.cpp` (300 x 300).
 * Even-odd fill rule with holes.
 * @see https://github.com/google/skia/blob/main/gm/crbug_908646.cpp
 */
class Crbug908646Gm : SkiaGm {
    override val name = "crbug_908646"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)
        val path = Path {
            moveTo(50f, 50f)
            lineTo(50f, 300f)
            lineTo(250f, 300f)
            lineTo(250f, 50f)
            moveTo(200f, 100f)
            lineTo(100f, 100f)
            lineTo(150f, 200f)
            moveTo(100f, 250f)
            lineTo(150f, 150f)
            lineTo(200f, 250f)
        }.apply { fillType = FillType.EVEN_ODD }
        canvas.drawPath(path, paint)
    }
}
