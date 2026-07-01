package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

class ManyCirclesGm : SkiaGm {
    override val name = "manycircles"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(1)
        val paint = Paint(antiAlias = true)
        var total = 10_000
        while (total-- > 0) {
            val x = rand.nextFloat() * kWidth - 100f
            val y = rand.nextFloat() * kHeight - 100f
            val w = rand.nextFloat() * 200f
            val circle = Rect.fromXYWH(x, y, w, w)
            canvas.drawOval(circle, paint.copy(color = genColor(rand)))
        }
    }

    private fun genColor(rand: Random): Color {
        val hue = rand.nextFloat() * 360f
        val sat = 0.5f + rand.nextFloat() * 0.5f
        val value = 0.5f + rand.nextFloat() * 0.5f
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
