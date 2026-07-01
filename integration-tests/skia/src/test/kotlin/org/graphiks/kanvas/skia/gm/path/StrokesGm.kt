package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/strokes.cpp`.
 * Tests round-stroked ovals and round-rects with 50 random shapes, with and without AA.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

class StrokesGm : SkiaGm {
    override val name = "strokes_round"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 17.3
    override val width = W
    override val height = H * 2

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        for (y in 0 until 2) {
            val aa = y != 0
            canvas.save()
            canvas.translate(0f, SH * y)
            canvas.clipRect(Rect.fromLTRB(2f, 2f, SW - 2f, SH - 2f))

            val rand = Random(0)
            for (i in 0 until N) {
                val (r1, c1) = rndRect(rand)
                canvas.drawOval(r1, Paint(
                    style = PaintStyle.STROKE,
                    strokeWidth = 9f / 2f,
                    antiAlias = aa,
                    color = c1,
                ))

                val (r2, c2) = rndRect(rand)
                val cr = CornerRadii(r2.width / 4f, r2.height / 4f)
                canvas.drawPath(
                    Path { }.apply { addRRect(RRect(r2, cr, cr, cr, cr)) },
                    Paint(
                        style = PaintStyle.STROKE,
                        strokeWidth = 9f / 2f,
                        antiAlias = aa,
                        color = c2,
                    ),
                )

                rndRect(rand) // discarded, advances rand state
            }
            canvas.restore()
        }
    }

    private fun rndRect(rand: Random): Pair<Rect, Color> {
        val x = rand.nextFloat() * W
        val y = rand.nextFloat() * H
        val w = rand.nextFloat() * (W shr 2)
        val h = rand.nextFloat() * (H shr 2)
        val hoffset = rand.nextFloat() * 2f - 1f
        val woffset = rand.nextFloat() * 2f - 1f

        val dx = -w / 2f + woffset
        val dy = -h / 2f + hoffset
        val r = Rect.fromLTRB(x + dx, y + dy, x + w + dx, y + h + dy)

        val c32 = rand.nextInt()
        val color = Color.fromRGBA(
            ((c32 ushr 16) and 0xFF) / 255f,
            ((c32 ushr 8) and 0xFF) / 255f,
            (c32 and 0xFF) / 255f,
            1f,
        )
        return Pair(r, color)
    }

    private companion object {
        const val W = 400
        const val H = 400
        const val N = 50
        const val SW = 400f
        const val SH = 400f
    }
}
