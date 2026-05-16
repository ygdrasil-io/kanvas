package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Port of Skia's `gm/stringart.cpp::StringArtGM` (440 × 440).
 *
 * String-art polyline : starting from the centre, walks 140 spokes
 * with `step = kAngle * π` and length growing as `length += angle / (π/2)`
 * until the spoke would extend past `(canvas_min - 10) / 2`. Pure trig
 * + lineTo. Stroked at 0-width hairline in `RGB565`-quantised dark
 * green (`0xFF007700`).
 */
public class StringArtGM : GM() {

    override fun getName(): String = "stringart"
    override fun getISize(): SkISize = SkISize.Make(K_WIDTH, K_HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val angle = K_ANGLE * PI.toFloat() + 0.5f * PI.toFloat()
        val size = min(K_WIDTH, K_HEIGHT).toFloat()
        val cx = 0.5f * K_WIDTH
        val cy = 0.5f * K_HEIGHT
        var length = 5f
        var step = angle

        val builder = SkPathBuilder()
        builder.moveTo(cx, cy)

        var i = 0
        while (i < K_MAX_NUM_STEPS && length < (0.5f * size - 10f)) {
            val rx = length * cos(step) + cx
            val ry = length * sin(step) + cy
            builder.lineTo(rx, ry)
            length += angle / (0.5f * PI.toFloat())
            step += angle
            i++
        }

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            color = ToolUtils.colorTo565(0xFF007700.toInt())
        }
        c.drawPath(builder.detach(), paint)
    }

    private companion object {
        const val K_WIDTH: Int = 440
        const val K_HEIGHT: Int = 440
        const val K_ANGLE: Float = 0.305f
        const val K_MAX_NUM_STEPS: Int = 140
    }
}
