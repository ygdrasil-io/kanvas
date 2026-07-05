package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/strokes.cpp:Strokes2GM`.
 * 25 stroked polylines per pane, each rotated 15° around the pane centre.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class Strokes2Gm : SkiaGm {
    override val name = "strokes_poly"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 14.5
    override val width = W
    override val height = H * 2

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val fPath = Path {
            val rng = Random(0)
            moveTo(0f, 0f)
            for (i in 0 until 13) {
                val x = rng.nextFloat() * (W shr 1)
                val y = rng.nextFloat() * (H shr 1)
                lineTo(x, y)
            }
        }

        for (y in 0 until 2) {
            val aa = y != 0
            canvas.save()
            canvas.translate(0f, SH * y)
            canvas.clipRect(Rect.fromLTRB(2f, 2f, SW - 2f, SH - 2f))

            val rand = Random(0)
            for (i in 0 until N / 2) {
                val color = rndColor(rand)
                val paint = Paint(
                    style = PaintStyle.STROKE,
                    strokeWidth = 9f / 2f,
                    antiAlias = aa,
                    color = color,
                )
                canvas.save()
                canvas.translate(SW / 2f, SH / 2f)
                canvas.rotate(15f * (i + 1))
                canvas.translate(-SW / 2f, -SH / 2f)
                canvas.drawPath(fPath, paint)
                canvas.restore()
            }
            canvas.restore()
        }
    }

    private fun rndColor(rand: Random): Color {
        val c32 = rand.nextInt()
        return Color.fromRGBA(
            ((c32 ushr 16) and 0xFF) / 255f,
            ((c32 ushr 8) and 0xFF) / 255f,
            (c32 and 0xFF) / 255f,
            1f,
        )
    }

    private companion object {
        const val W: Int = 400
        const val H: Int = 400
        const val N: Int = 50
        const val SW: Float = 400f
        const val SH: Float = 400f
    }
}
