package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathMeasure
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkDegreesToRadians
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/addarc.cpp::DEF_SIMPLE_GM(addarc_meas, …)`.
 *
 * Draws a circumscribed `400`-radius black AA-stroked oval, then for
 * every `10°` step `deg ∈ [0, 360)` overlays:
 *  - a black radial line from the origin to the oval-boundary point at
 *    angle `deg`, and
 *  - a red line from the origin to the [SkPathMeasure.getPosTan]
 *    position corresponding to arc-length `deg · π/180 · R` along an
 *    arc built by `SkPathBuilder().addArc(oval, 0, deg)`.
 *
 * If the path-measure machinery is correct the two endpoints coincide
 * (the red line just retraces the black radial), so the final image is
 * the oval + 36 black radii with a few short red over-strokes where
 * the cubic flattening introduces sub-pixel drift. The GM exists
 * specifically to surface that drift.
 *
 * Reference image: `addarc_meas.png`, `2·R + 40 = 840 × 840`,
 * default white BG.
 */
public class AddArcMeasGM : GM() {

    private companion object {
        const val R: Float = 400f
    }

    override fun getName(): String = "addarc_meas"
    override fun getISize(): SkISize = SkISize.Make((2 * R + 40).toInt(), (2 * R + 40).toInt())

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(R + 20f, R + 20f)

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }

        val measPaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
        }

        val oval = SkRect.MakeLTRB(-R, -R, R, R)
        c.drawOval(oval, paint)

        var deg = 0f
        while (deg < 360f) {
            val rad = SkDegreesToRadians(deg)
            val rx = cos(rad) * R
            val ry = sin(rad) * R

            c.drawLine(0f, 0f, rx, ry, paint)

            val arc = SkPathBuilder().addArc(oval, 0f, deg).detach()
            val meas = SkPathMeasure(arc, false)
            val arcLen = rad * R
            val pos = SkPoint()
            if (meas.getPosTan(arcLen, pos, null)) {
                c.drawLine(0f, 0f, pos.fX, pos.fY, measPaint)
            }
            deg += 10f
        }
    }
}
