package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.random.Random

/**
 * Port of Skia's `gm/beziers.cpp`.
 * 10 random AA-stroked quad paths + 10 cubic paths with varying
 * stroke widths and colors.
 * @see https://github.com/google/skia/blob/main/gm/beziers.cpp
 */
class BeziersGm : SkiaGm {
    override val name = "beziers"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(0)
        for (i in 0 until N) {
            val (path, paint) = rndQuad(rand)
            canvas.drawPath(path, paint)
        }
        canvas.translate(0f, SH)
        for (i in 0 until N) {
            val (path, paint) = rndCubic(rand)
            canvas.drawPath(path, paint)
        }
    }

    private fun rndQuad(rand: Random): Pair<Path, Paint> {
        val a = rand.nextFloat() * W
        val b = rand.nextFloat() * H
        val path = Path {
            moveTo(a, b)
            for (x in 0 until 2) {
                val cc = rand.nextFloat() * (W - W / 4f) + W / 4f
                val d = rand.nextFloat() * H
                val e = rand.nextFloat() * W
                val f = rand.nextFloat() * (H - H / 4f) + H / 4f
                quadTo(cc, d, e, f)
            }
        }
        val c32 = rand.nextInt()
        val color = Color.fromRGBA(
            r = ((c32 ushr 16) and 0xFF) / 255f,
            g = ((c32 ushr 8) and 0xFF) / 255f,
            b = (c32 and 0xFF) / 255f,
            a = 1f,
        )
        var strokeW = rand.nextFloat() * 4f + 1f
        strokeW *= strokeW
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = strokeW,
            antiAlias = true,
            color = color,
        )
        return Pair(path, paint)
    }

    private fun rndCubic(rand: Random): Pair<Path, Paint> {
        val a = rand.nextFloat() * W
        val b = rand.nextFloat() * H
        val path = Path {
            moveTo(a, b)
            for (x in 0 until 2) {
                val cc = rand.nextFloat() * (W - W / 4f) + W / 4f
                val d = rand.nextFloat() * H
                val e = rand.nextFloat() * W
                val f = rand.nextFloat() * (H - H / 4f) + H / 4f
                val g = rand.nextFloat() * (W - W / 4f) + W / 4f
                val h_ = rand.nextFloat() * (H - H / 4f) + H / 4f
                cubicTo(cc, d, e, f, g, h_)
            }
        }
        val c32 = rand.nextInt()
        val color = Color.fromRGBA(
            r = ((c32 ushr 16) and 0xFF) / 255f,
            g = ((c32 ushr 8) and 0xFF) / 255f,
            b = (c32 and 0xFF) / 255f,
            a = 1f,
        )
        var strokeW = rand.nextFloat() * 4f + 1f
        strokeW *= strokeW
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = strokeW,
            antiAlias = true,
            color = color,
        )
        return Pair(path, paint)
    }

    private companion object {
        const val W: Float = 400f
        const val H: Float = 400f
        const val N: Int = 10
        const val SH: Float = 400f
    }
}
