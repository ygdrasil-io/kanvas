package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/cubicpaths.cpp::bug6083` (DEF_SIMPLE_GM, 100 x 50).
 *
 * Two thin-stroked move/line/cubic paths drawn under a large negative
 * translate (`-500, -130`). The cubics are nearly identical (`p2.y`
 * differs by ~0.2 between the two), exercising stroker numerical
 * robustness on near-coincident outlines under big translates.
 * @see https://github.com/google/skia/blob/main/gm/cubicpaths.cpp
 */
class Bug6083Gm : SkiaGm {
    override val name = "bug6083"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 57.2
    override val width = 100
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 15f,
        )
        canvas.translate(-500f, -130f)

        val p1x = 526.109f; val p1y = 155.200f
        val p3x = 526.109f; val p3y = 241.840f

        val path1 = Path {
            moveTo(500.988f, 155.200f)
            lineTo(p1x, p1y)
            cubicTo(p1x, p1y, 525.968f, 212.968f, p3x, p3y)
        }
        canvas.drawPath(path1, paint)
        canvas.translate(50f, 0f)

        val path2 = Path {
            moveTo(500.988f, 155.200f)
            lineTo(p1x, p1y)
            cubicTo(p1x, p1y, 525.968f, 213.172f, p3x, p3y)
        }
        canvas.drawPath(path2, paint)
    }
}
