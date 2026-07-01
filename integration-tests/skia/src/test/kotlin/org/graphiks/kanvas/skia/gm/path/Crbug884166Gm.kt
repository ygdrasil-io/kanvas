package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_884166.cpp`.
 * Reduced from a Chromium polygon-fill bug. A single 8-vertex line-only
 * contour with a near-vertical sliver is filled with AA + kWinding rule.
 * @see https://github.com/google/skia/blob/main/gm/crbug_884166.cpp
 */
class Crbug884166Gm : SkiaGm {
    override val name = "crbug_884166"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.FILL,
        )
        val path = Path {
            moveTo(153.25f, 280.75f)
            lineTo(161.75f, 281.75f)
            lineTo(164.25f, 282.00f)
            lineTo(  0.00f, 276.00f)
            lineTo(161.50f,   0.00f)
            lineTo(286.25f, 231.25f)
            lineTo(163.75f, 282.00f)
            lineTo(150.00f, 280.00f)
        }
        canvas.drawPath(path, paint)
    }
}
