package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Dashing5GM.
 *
 * The original rotates the canvas 90° and draws long vertical/horizontal
 * dashed lines to stress dash decomposition. Since GmCanvas does not
 * support `rotate` or `concat`, we manually swap coordinates to
 * approximate the 90° rotation.
 */
/**
 * Port of Skia's `gm/dashing.cpp` (Dashing5GM).
 * Long dashed lines with rotation, cycling stroke widths and rainbow colors.
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class Dashing5Gm(private val doAA: Boolean) : SkiaGm {
    constructor() : this(true)

    override val name = if (doAA) "dashing5_aa" else "dashing5_bw"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 200

    private fun drawLine(
        canvas: GmCanvas,
        on: Int, off: Int,
        paint: Paint,
        startX: Float, startY: Float,
        phase: Float,
        finalX: Float, finalY: Float,
    ) {
        val p = paint.copy(pathEffect = PathEffect.Dash(floatArrayOf(on.toFloat(), off.toFloat()), phase))
        canvas.drawLine(startX, startY, finalX, finalY, p)
    }

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

        // 90° rotation: (x, y) → (-y, x)
        // Original x-loop draws vertical lines at x positions 0..200
        // After rotation: horizontal lines at y = x positions
        var phase = 0
        for (x in 0 until 200 step 10) {
            paint = paint.copy(strokeWidth = (phase + 1).toFloat(), color = colors[phase])
            val sign = if ((x % 20) != 0) 1 else -1
            drawLine(
                canvas, kOn, kOff, paint,
                -sign * 10003f, x.toFloat(), phase.toFloat(),
                sign * 10003f, x.toFloat(),
            )
            phase = (phase + 1) % kIntervalLength
        }

        // Original y-loop draws horizontal lines at y positions -400..0
        // After rotation: vertical lines at x = -y
        for (y in -400 until 0 step 10) {
            paint = paint.copy(strokeWidth = (phase + 1).toFloat(), color = colors[phase])
            val sign = if ((y % 20) != 0) 1 else -1
            drawLine(
                canvas, kOn, kOff, paint,
                (-y).toFloat(), -sign * 10003f, phase.toFloat(),
                (-y).toFloat(), sign * 10003f,
            )
            phase = (phase + 1) % kIntervalLength
        }
    }
}
