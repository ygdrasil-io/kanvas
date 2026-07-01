package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/ovals.cpp`.
 * 5x8 grid of stroked/filled ovals under various matrices and paint styles.
 * @see https://github.com/google/skia/blob/main/gm/ovals.cpp
 */
class OvalGm : SkiaGm {
    override val name = "ovals"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 900

    private val paints: List<Paint> = listOf(
        Paint(),
        Paint(antiAlias = true),
        Paint(antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 5f),
        Paint(antiAlias = true, style = PaintStyle.STROKE),
        Paint(antiAlias = true, style = PaintStyle.STROKE_AND_FILL, strokeWidth = 3f),
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(1)
        canvas.translate(20f, 20f)
        val kOval = Rect.fromLTRB(-20f, -30f, 20f, 30f)

        val kXStart = 60f
        val kYStart = 80f
        val kXStep = 150
        val kYStep = 160
        val maxX = 8

        val rectPaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = Color.fromRGBA(0xCC / 255f, 0xCC / 255f, 0xCC / 255f, 1f),
        )

        var testCount = 0
        for (i in paints.indices) {
            for (j in 0 until 8) {
                canvas.save()
                val dx = kXStart + kXStep * (testCount % maxX) + 0.25f
                val dy = kYStart + kYStep * (testCount / maxX) + 0.75f
                canvas.translate(dx, dy)
                applyMatrix(canvas, j)

                val color = genColor(rand)
                val paint = paints[i].copy(color = color)

                canvas.drawRect(kOval, rectPaint)
                canvas.drawOval(kOval, paint)

                canvas.restore()
                testCount += 1
            }
        }

        for (i in paints.indices) {
            val oval = Rect.fromLTRB(-20f, -60f, 20f, 60f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 2.55f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i].copy(color = genColor(rand))
            canvas.drawRect(oval, rectPaint)
            canvas.drawOval(oval, paint)
            canvas.restore()
        }

        for (i in paints.indices) {
            val oval = Rect.fromLTRB(-80f, -30f, 80f, 30f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 4 + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
            val paint = paints[i].copy(color = genColor(rand))
            canvas.drawRect(oval, rectPaint)
            canvas.drawOval(oval, paint)
            canvas.restore()
        }

        for (i in paints.indices) {
            val oval = Rect.fromLTRB(0f, -60f, 1f, 60f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 3.25f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i].copy(color = genColor(rand))
            canvas.drawOval(oval, paint)
            canvas.restore()
        }

        for (i in paints.indices) {
            val oval = Rect.fromLTRB(-80f, -1f, 80f, 0f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 2.5f + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
            val paint = paints[i].copy(color = genColor(rand))
            canvas.drawOval(oval, paint)
            canvas.restore()
        }

        for (i in paints.indices) {
            val oval = Rect.fromLTRB(-30f, -30f, 30f, 30f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 5 + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
            val paint = paints[i].copy(color = genColor(rand))
            canvas.drawRect(oval, rectPaint)
            canvas.drawOval(oval, paint)
            canvas.restore()
        }
    }

    private fun applyMatrix(canvas: GmCanvas, index: Int) {
        when (index) {
            1 -> canvas.scale(3f, 2f)
            2 -> canvas.scale(2f, 2f)
            3 -> canvas.scale(1f, 2f)
            4 -> canvas.scale(4f, 1f)
            5 -> canvas.rotate(30f)
            6 -> canvas.skew(0.5f, 0f)
            7 -> canvas.scale(1f, -1f)
        }
    }

    private fun genColor(rand: Random): Color {
        val h = rand.nextFloat() * 360f
        val s = rand.nextFloat() * 0.25f + 0.75f
        val v = rand.nextFloat() * 0.25f + 0.75f
        val (r, g, b) = hsvToRgb(h, s, v)
        return Color.fromRGBA(r, g, b, 1f)
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float): Triple<Float, Float, Float> {
        val c = v * s
        val hp = h / 60f
        val x = c * (1f - kotlin.math.abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt() % 6) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return Triple(r1 + m, g1 + m, b1 + m)
    }
}
