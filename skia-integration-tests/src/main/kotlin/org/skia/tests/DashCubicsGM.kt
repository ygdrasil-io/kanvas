package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.skia.utils.SkParsePath

/**
 * Port of Skia's `gm/dashcubics.cpp::DEF_SIMPLE_GM(dashcubics, …)`
 * (865 × 750).
 *
 * Renders a "flower" cubic-Bézier SVG path four times in a 2 × 2
 * grid, with three overlaid paints :
 *
 *  1. Black fat stroke (width 42, default join / round join).
 *  2. Red half-width dashed stroke (width 21, dash intervals
 *     `(5 or 5.0002, 10)` depending on the column).
 *  3. Green hairline (width 0).
 *
 * The `5 + 0.0001 + 0.0001` quirk in the left column triggers the
 * dasher's "shouldn't-be-integer" code path — it stays as a
 * separate variant so we don't accidentally optimise it away.
 */
public class DashCubicsGM : GM() {

    override fun getName(): String = "dashcubics"
    override fun getISize(): SkISize = SkISize.Make(865, 750)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkParsePath.FromSVGString(SVG_DATA) ?: SkPathBuilder().detach()
        c.translate(-35f, -55f)
        for (x in 0 until 2) {
            for (y in 0 until 2) {
                c.save()
                c.translate(x * 430f, y * 355f)
                val onLen = 5f + if (x != 0) 0f else (0.0001f + 0.0001f)
                val intervals = floatArrayOf(onLen, 10f)
                val join = if (y != 0) SkPaint.Join.kMiter_Join else SkPaint.Join.kRound_Join
                drawFlower(c, path, intervals, join)
                c.restore()
            }
        }
    }

    private fun drawFlower(
        canvas: SkCanvas,
        path: SkPath,
        intervals: FloatArray,
        join: SkPaint.Join,
    ) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            setStroke(true)
            strokeJoin = join
            strokeWidth = 42f
        }
        canvas.drawPath(path, paint)

        paint.color = SK_ColorRED
        paint.strokeWidth = 21f
        paint.pathEffect = SkDashPathEffect.Make(intervals, 0f)
        canvas.drawPath(path, paint)

        paint.color = SK_ColorGREEN
        paint.pathEffect = null
        paint.strokeWidth = 0f
        canvas.drawPath(path, paint)
    }

    public companion object {
        private const val SVG_DATA: String =
            "M 337,98 C 250,141 250,212 250,212 C 250,212 250,212 250,212" +
                "C 250,212 250,212 250,212 C 250,212 250,141 163,98 C 156,195 217,231 217,231" +
                "C 217,231 217,231 217,231 C 217,231 217,231 217,231 C 217,231 156,195 75,250" +
                "C 156,305 217,269 217,269 C 217,269 217,269 217,269 C 217,269 217,269 217,269" +
                "C 217,269 156,305 163,402 C 250,359 250,288 250,288 C 250,288 250,288 250,288" +
                "C 250,288 250,288 250,288 C 250,288 250,359 338,402 C 345,305 283,269 283,269" +
                "C 283,269 283,269 283,269 C 283,269 283,269 283,269 C 283,269 345,305 425,250" +
                "C 344,195 283,231 283,231 C 283,231 283,231 283,231 C 283,231 283,231 283,231" +
                "C 283,231 344,195 338,98"
    }
}
