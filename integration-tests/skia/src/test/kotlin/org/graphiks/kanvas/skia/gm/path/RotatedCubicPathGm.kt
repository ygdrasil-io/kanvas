package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/pathfill.cpp` `DEF_SIMPLE_GM(rotatedcubicpath, …)`.
 * Two stacked cubic-only closed paths — one axis-aligned in blue,
 * one rotated 90° in red.
 * @see https://github.com/google/skia/blob/main/gm/pathfill.cpp
 */
class RotatedCubicPathGm : SkiaGm {
    override val name = "rotatedcubicpath"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 90.6
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.FILL,
        )

        canvas.translate(50f, 50f)
        val path = Path {
            moveTo(48f, -23f)
            cubicTo(48f, -29.5f, 6f, -30f, 6f, -30f)
            cubicTo(6f, -30f, 2f, 0f, 2f, 0f)
            cubicTo(2f, 0f, 44f, -21.5f, 48f, -23f)
            close()
        }

        canvas.drawPath(path, paint.copy(color = Color.BLUE))

        canvas.rotate(90f)
        canvas.drawPath(path, paint.copy(color = Color.RED))
    }
}
