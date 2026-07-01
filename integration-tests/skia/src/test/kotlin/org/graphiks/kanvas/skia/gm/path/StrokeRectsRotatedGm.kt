package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/strokerects.cpp` rotated variant.
 * 100 random stroked rects per pane, rotated 45°.
 * @see https://github.com/google/skia/blob/main/gm/strokerects.cpp
 */
class StrokeRectsRotatedGm : SkiaGm {
    override val name = "strokerects_rotated"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 61.9
    override val width = W * 2
    override val height = H * 2

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.rotate(45f)

        for (y in 0 until 2) {
            val aa = y != 0
            for (x in 0 until 2) {
                val strokeWidth = (x * 3).toFloat()
                val paint = Paint(
                    style = PaintStyle.STROKE,
                    antiAlias = aa,
                    strokeWidth = strokeWidth,
                )

                canvas.save()
                canvas.translate((SW * x).toFloat(), (SH * y).toFloat())
                canvas.clipRect(Rect.fromLTRB(2f, 2f, SW - 2f, SH - 2f))

                val rand = Random(0)
                for (i in 0 until N) {
                    val r = rndRect(rand)
                    canvas.drawRect(r, paint)
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
