package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/strokerects.cpp:StrokeRectsGM(false)` — the
 * non-rotated variant. 2 x 2 panes (AA off / AA on x strokeWidth 0 / 3)
 * of N = 100 random stroked rects each. Each pane uses a fresh default-seeded
 * [Random] so the same rects are drawn across all 4 panes.
 */
/**
 * Port of Skia's `gm/strokerects.cpp`.
 * 400 random stroked rects with AA on/off and varying stroke widths.
 * @see https://github.com/google/skia/blob/main/gm/strokerects.cpp
 */
class StrokeRectsGm : SkiaGm {
    override val name = "strokerects"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 58.17
    override val width = W * 2
    override val height = H * 2

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(style = PaintStyle.STROKE)

        for (y in 0 until 2) {
            val aa = y != 0
            for (x in 0 until 2) {
                val strokeWidth = (x * 3).toFloat()
                val cellPaint = Paint(
                    style = PaintStyle.STROKE,
                    antiAlias = aa,
                    strokeWidth = strokeWidth,
                )

                canvas.save()
                canvas.translate((SW * x).toFloat(), (SH * y).toFloat())

                val rand = Random(0)
                for (i in 0 until N) {
                    val r = rndRect(rand)
                    canvas.drawRect(r, cellPaint)
                }
                canvas.restore()
            }
        }
    }

    private fun rndRect(rand: Random): Rect {
        val x = rand.nextFloat() * W
        val y = rand.nextFloat() * H
        val w = rand.nextFloat() * (W shr 2)
        val h = rand.nextFloat() * (H shr 2)
        val hoffset = rand.nextFloat() * 2f - 1f
        val woffset = rand.nextFloat() * 2f - 1f

        val dx = -w / 2f + woffset
        val dy = -h / 2f + hoffset
        return Rect.fromLTRB(x + dx, y + dy, x + w + dx, y + h + dy)
    }

    private companion object {
        const val W: Int = 400
        const val H: Int = 400
        const val N: Int = 100
        const val SW: Int = 400
        const val SH: Int = 400
    }
}
