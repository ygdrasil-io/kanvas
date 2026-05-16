package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/conicpaths.cpp` `DEF_SIMPLE_GM(arccirclegap, …)`.
 *
 * Stress test for the gap that can appear between a stroked circle and
 * a stroked tangent-arc that should overlap on top of it. Both shapes
 * use the same large radius (~1097) and the arc starts at a precise
 * sub-pixel coordinate on the circle's circumference.
 *
 * Reference image: `arccirclegap.png`, 250 × 250, default white BG.
 *
 * Stresses the [SkPathBuilder.arcTo] tangent variant on a near-real-
 * world circle radius — a regression for a Skia sub-pixel gap bug.
 */
public class ArcCircleGapGM : GM() {

    override fun getName(): String = "arccirclegap"
    override fun getISize(): SkISize = SkISize.Make(250, 250)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(50f, 100f)

        val cx = 1052.5390625f
        val cy = 506.8760978034711f
        val radius = 1096.702150363923f

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        c.drawCircle(cx, cy, radius, paint)

        val path = SkPathBuilder()
            .moveTo(288.88884710654133f, -280.26680862609f)
            .arcTo(0f, 0f, -39.00216443306411f, 400.6058925796476f, radius)
            .detach()
        paint.color = 0xFF007F00.toInt()
        c.drawPath(path, paint)
    }
}
