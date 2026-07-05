package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/conicpaths.cpp` `DEF_SIMPLE_GM(arccirclegap, …)`.
 * Stress test for the gap that can appear between a stroked circle and
 * a stroked tangent-arc that should overlap on top of it. Both shapes
 * use the same large radius (~1097) and the arc starts at a precise
 * sub-pixel coordinate on the circle's circumference.
 * Stresses the Path.arcTo tangent variant on a near-real-world circle
 * radius — a regression for a Skia sub-pixel gap bug.
 * @see https://github.com/google/skia/blob/main/gm/conicpaths.cpp
 */
class ArcCircleGapGm : SkiaGm {
    override val name = "arccirclegap"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(50f, 100f)

        val cx = 1052.5390625f
        val cy = 506.8760978034711f
        val radius = 1096.702150363923f

        var paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE
        )
        canvas.drawCircle(cx, cy, radius, paint)

        val path = Path {
            moveTo(288.88884710654133f, -280.26680862609f)
            arcTo(0f, 0f, -39.00216443306411f, false, true, 400.6058925796476f, radius)
        }
        paint = paint.copy(color = Color.fromRGBA(0f, 127f / 255f, 0f, 1f))
        canvas.drawPath(path, paint)
    }
}
