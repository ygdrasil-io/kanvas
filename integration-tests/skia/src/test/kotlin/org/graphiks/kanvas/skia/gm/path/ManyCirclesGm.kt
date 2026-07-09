package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/manycircles.cpp`.
 * Fills canvas with 10,000 randomly positioned and colored circles.
 * @see https://github.com/google/skia/blob/main/gm/manycircles.cpp
 */

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.skia.SkiaRandom
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ManyCirclesGm : SkiaGm {
    override val name = "manycircles"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = SkiaRandom(1u)
        val paint = Paint(antiAlias = true)
        var total = 10_000
        while (total-- > 0) {
            val x = rand.nextF() * kWidth - 100f
            val y = rand.nextF() * kHeight - 100f
            val w = rand.nextF() * 200f
            val circle = Rect.fromXYWH(x, y, w, w)
            canvas.drawOval(circle, paint.copy(color = genColor(rand)))
        }
    }

    private fun genColor(rand: SkiaRandom): Color {
        val hue = rand.nextF() * 360f
        val sat = 0.5f + rand.nextF() * 0.5f
        val value = 0.5f + rand.nextF() * 0.5f
        val c = value * sat
        val hp = hue / 60f
        val xVal = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, xVal, 0f)
            hp < 2f -> Triple(xVal, c, 0f)
            hp < 3f -> Triple(0f, c, xVal)
            hp < 4f -> Triple(0f, xVal, c)
            hp < 5f -> Triple(xVal, 0f, c)
            else -> Triple(c, 0f, xVal)
        }
        val m = value - c
        return Color.fromRGBA(r1 + m, g1 + m, b1 + m)
    }

    private companion object {
        const val kWidth: Int = 800
        const val kHeight: Int = 600
    }
}
