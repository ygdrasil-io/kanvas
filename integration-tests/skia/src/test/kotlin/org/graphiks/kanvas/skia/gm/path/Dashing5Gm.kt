package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/dashing.cpp` (Dashing5GM).
 * Long dashed lines with rotation, cycling stroke widths and rainbow colors.
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class Dashing5Gm(private val doAA: Boolean) : SkiaGm {
    constructor() : this(true)

    override val name = if (doAA) "dashing5_aa" else "dashing5_bw"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kOn = 4
        val kOff = 4
        val kIntervalLength = kOn + kOff

        val colors = listOf(
            Color.RED, Color.GREEN, Color.BLUE,
            Color.fromRGBA(0f, 1f, 1f, 1f),
            Color.fromRGBA(1f, 0f, 1f, 1f),
            Color.fromRGBA(1f, 1f, 0f, 1f),
            Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
            Color.fromRGBA(0.25f, 0.25f, 0.25f, 1f),
        )

        var paint = Paint(
            style = PaintStyle.STROKE,
            antiAlias = doAA,
        )

        canvas.save()
        canvas.translate(100f, 10f)
        canvas.rotate(90f)

        var phase = 0
        for (x in 0 until 200 step 10) {
            paint = paint.copy(strokeWidth = (phase + 1).toFloat(), color = colors[phase])
            val sign = if ((x % 20) != 0) 1 else -1
            val p = paint.copy(pathEffect = PathEffect.Dash(floatArrayOf(kOn.toFloat(), kOff.toFloat()), phase.toFloat()))
            canvas.drawLine(x.toFloat(), (-sign * 10003).toFloat(), x.toFloat(), (sign * 10003).toFloat(), p)
            phase = (phase + 1) % kIntervalLength
        }

        for (y in -400 until 0 step 10) {
            paint = paint.copy(strokeWidth = (phase + 1).toFloat(), color = colors[phase])
            val sign = if ((y % 20) != 0) 1 else -1
            val p = paint.copy(pathEffect = PathEffect.Dash(floatArrayOf(kOn.toFloat(), kOff.toFloat()), phase.toFloat()))
            canvas.drawLine((-sign * 10003).toFloat(), y.toFloat(), (sign * 10003).toFloat(), y.toFloat(), p)
            phase = (phase + 1) % kIntervalLength
        }

        canvas.restore()
    }
}
