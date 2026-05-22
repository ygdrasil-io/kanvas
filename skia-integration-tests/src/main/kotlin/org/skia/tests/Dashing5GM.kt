package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorMAGENTA
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint

/**
 * Port of Skia's `gm/dashing.cpp::Dashing5GM` (400 × 200) — comes in
 * two variants : `dashing5_bw` (AA off) and `dashing5_aa` (AA on),
 * surfaced by the [doAA] constructor flag.
 *
 * Rotates the canvas 90° then walks two columns of long dashed lines
 * with cycling stroke widths `(1..8)` and rainbow colors, on/off
 * intervals `(4, 4)`. Lines extend ±10003 px out of the visible area
 * to exercise the dash decomposition over very long primitives.
 *
 * The AA variant is the more interesting one (the rotation forces the
 * stroker / AA edge code paths) ; BW is the points-fast-path probe.
 */
public class Dashing5GM(private val doAA: Boolean) : GM() {

    public constructor() : this(true)

    override fun getName(): String = if (doAA) "dashing5_aa" else "dashing5_bw"
    override fun getISize(): SkISize = SkISize.Make(400, 200)

    private fun drawLine(
        canvas: SkCanvas,
        on: Int, off: Int,
        paint: SkPaint,
        startX: Float, startY: Float,
        phase: Float,
        finalX: Float, finalY: Float,
    ) {
        val p = paint.copy()
        p.pathEffect = SkDashPathEffect.Make(floatArrayOf(on.toFloat(), off.toFloat()), phase)
        canvas.drawLine(startX, startY, finalX, finalY, p)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val kOn = 4
        val kOff = 4
        val kIntervalLength = kOn + kOff

        val colors = intArrayOf(
            SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE, SK_ColorCYAN,
            SK_ColorMAGENTA, SK_ColorYELLOW, SK_ColorGRAY, SK_ColorDKGRAY,
        )

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = doAA
        }

        val rot = SkMatrix.MakeRotate(90f)
        c.concat(rot)

        var phase = 0
        for (x in 0 until 200 step 10) {
            paint.strokeWidth = (phase + 1).toFloat()
            paint.color = colors[phase]
            val sign = if ((x % 20) != 0) 1 else -1
            drawLine(
                c, kOn, kOff, paint,
                x.toFloat(), -sign * 10003f, phase.toFloat(),
                x.toFloat(), sign * 10003f,
            )
            phase = (phase + 1) % kIntervalLength
        }

        for (y in -400 until 0 step 10) {
            paint.strokeWidth = (phase + 1).toFloat()
            paint.color = colors[phase]
            val sign = if ((y % 20) != 0) 1 else -1
            drawLine(
                c, kOn, kOff, paint,
                -sign * 10003f, y.toFloat(), phase.toFloat(),
                sign * 10003f, y.toFloat(),
            )
            phase = (phase + 1) % kIntervalLength
        }
    }
}
