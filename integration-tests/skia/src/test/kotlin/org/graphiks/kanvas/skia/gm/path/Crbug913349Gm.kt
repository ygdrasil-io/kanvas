package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_913349.cpp` (500 x 600).
 * Near-zero-area sliver polygon filled with AA + kWinding.
 * @see https://github.com/google/skia/blob/main/gm/crbug_913349.cpp
 */
class Crbug913349Gm : SkiaGm {
    override val name = "crbug_913349"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true, style = PaintStyle.FILL)
        val path = Path {
            moveTo(349.5f, 225.75f)
            lineTo(96.5f, 74f)
            lineTo(500.50f, 226f)
            lineTo(350f, 226f)
            lineTo(350f, 224f)
        }
        canvas.drawPath(path, paint)
    }
}
