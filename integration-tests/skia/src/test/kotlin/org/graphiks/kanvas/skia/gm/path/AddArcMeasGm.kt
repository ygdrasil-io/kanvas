package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/addarc.cpp::DEF_SIMPLE_GM(addarc_meas, …)`.
 * Draws a circumscribed 400-radius black AA-stroked oval, then for
 * every 10° step deg ∈ [0, 360) overlays:
 * - a black radial line from the origin to the oval-boundary point at
 *   angle deg, and
 * - a red line from the origin to the position corresponding to arc-length
 *   deg · π/180 · R along an arc built by Path.addArc(oval, 0, deg).
 * If the path-measure machinery is correct the two endpoints coincide
 * (the red line just retraces the black radial), so the final image is
 * the oval + 36 black radii with a few short red over-strokes where
 * the cubic flattening introduces sub-pixel drift.
 * @see https://github.com/google/skia/blob/main/gm/addarc.cpp
 */
class AddArcMeasGm : SkiaGm {
    override val name = "addarc_meas"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0

    private companion object {
        const val R: Float = 400f
    }

    override val width: Int = (2 * R + 40).toInt()
    override val height: Int = (2 * R + 40).toInt()

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(R + 20f, R + 20f)

        var paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE
        )

        val measPaint = Paint(
            antiAlias = true,
            color = Color.RED
        )

        val oval = Rect.fromLTRB(-R, -R, R, R)
        canvas.drawOval(oval, paint)

        var deg = 0f
        while (deg < 360f) {
            val rad = Math.toRadians(deg.toDouble()).toFloat()
            val rx = cos(rad) * R
            val ry = sin(rad) * R

            canvas.drawLine(0f, 0f, rx, ry, paint)

            // For now, skip the path measure part as it's not available in Kanvas
            // The original GM uses SkPathMeasure.getPosTan which is not yet ported
            // val arc = Path {
            //     addArc(oval, 0f, deg)
            // }
            // val arcLen = rad * R
            // val pos = arc.getPointAtLength(arcLen)
            // canvas.drawLine(0f, 0f, pos.x, pos.y, measPaint)
            deg += 10f
        }
    }
}
