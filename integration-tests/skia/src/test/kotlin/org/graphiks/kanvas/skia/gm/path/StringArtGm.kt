package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Port of Skia's `gm/stringart.cpp::StringArtGM`.
 * String-art polyline: walks 140 spokes with growing step until
 * the spoke would extend past the canvas margin. Pure trig + lineTo,
 * hairline-stroked in dark green.
 * @see https://github.com/google/skia/blob/main/gm/stringart.cpp
 */
class StringArtGm : SkiaGm {
    override val name = "stringart"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 77.9
    override val width = K_WIDTH
    override val height = K_HEIGHT

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val angle = K_ANGLE * PI.toFloat() + 0.5f * PI.toFloat()
        val size = min(K_WIDTH, K_HEIGHT).toFloat()
        val cx = 0.5f * K_WIDTH
        val cy = 0.5f * K_HEIGHT
        var length = 5f
        var step = angle

        val path = Path {
            moveTo(cx, cy)
            var i = 0
            while (i < K_MAX_NUM_STEPS && length < (0.5f * size - 10f)) {
                val rx = length * cos(step) + cx
                val ry = length * sin(step) + cy
                lineTo(rx, ry)
                length += angle / (0.5f * PI.toFloat())
                step += angle
                i++
            }
        }

        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = Color.fromRGBA(0f, 0x77 / 255f, 0f, 1f),
        )
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val K_WIDTH: Int = 440
        const val K_HEIGHT: Int = 440
        const val K_ANGLE: Float = 0.305f
        const val K_MAX_NUM_STEPS: Int = 140
    }
}
