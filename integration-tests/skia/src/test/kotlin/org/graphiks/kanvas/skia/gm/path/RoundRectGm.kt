package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/roundrects.cpp` (`RoundRectGM`).
 *
 * A 5x8 grid of stroked/filled rrects under 5 paints and 8 matrices,
 * plus special-case rows (tall/wide/skinny/short/gradient/strokes/OOO).
 * @see https://github.com/google/skia/blob/main/gm/roundrects.cpp
 */
class RoundRectGm : SkiaGm {
    override val name = "roundrects"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1200
    override val height = 900

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(1)
        canvas.translate(20f, 20f)
        val kRect = Rect.fromLTRB(-20f, -30f, 20f, 30f)
        val circleRRect = RRect(kRect, 5f)

        val kXStart = 60f
        val kYStart = 80f
        val kXStep = 150
        val kYStep = 160
        val maxX = 8

        val rectPaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = Color.fromRGBA(204f / 255f, 204f / 255f, 204f / 255f),
        )

        val paints: List<(Random) -> Paint> = listOf(
            { _ -> Paint() },
            { _ -> Paint(antiAlias = true) },
            { _ -> Paint(antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 5f) },
            { _ -> Paint(antiAlias = true, style = PaintStyle.STROKE) },
            { _ -> Paint(antiAlias = true, style = PaintStyle.STROKE_AND_FILL, strokeWidth = 3f) },
        )

        val matrices: List<Matrix33> = listOf(
            Matrix33.identity(),
            Matrix33.scale(3f, 2f),
            Matrix33.scale(2f, 2f),
            Matrix33.scale(1f, 2f),
            Matrix33.scale(4f, 1f),
            Matrix33.rotate(90f),
            Matrix33.skew(2f, 3f),
            Matrix33.rotate(60f),
        )

        var testCount = 0
        for (i in paints.indices) {
            for (j in matrices.indices) {
                canvas.save()
                val dx = kXStart + kXStep * (testCount % maxX) + 0.25f
                val dy = kYStart + kYStep * (testCount / maxX) + 0.75f
                canvas.translate(dx, dy)
                canvas.concat(matrices[j])

                val color = genColor(rand)
                val paint = paints[i](rand).copy(color = color)

                canvas.drawRect(kRect, rectPaint)
                canvas.drawRRect(circleRRect, paint)

                canvas.restore()
                testCount += 1
            }
        }

        // Tall and skinny
        for (i in paints.indices) {
            val rect = Rect.fromLTRB(-20f, -60f, 20f, 60f)
            val ellipseRect = RRect(rect, CornerRadii(5f, 10f), CornerRadii(5f, 10f), CornerRadii(5f, 10f), CornerRadii(5f, 10f))
            canvas.save()
            canvas.translate(kXStart + kXStep * 2.55f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i](rand).copy(color = genColor(rand))
            canvas.drawRect(rect, rectPaint)
            canvas.drawRRect(ellipseRect, paint)
            canvas.restore()
        }

        // Wide and short
        for (i in paints.indices) {
            val rect = Rect.fromLTRB(-80f, -30f, 80f, 30f)
            val ellipseRect = RRect(rect, CornerRadii(20f, 5f), CornerRadii(20f, 5f), CornerRadii(20f, 5f), CornerRadii(20f, 5f))
            canvas.save()
            canvas.translate(kXStart + kXStep * 4 + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
            val paint = paints[i](rand).copy(color = genColor(rand))
            canvas.drawRect(rect, rectPaint)
            canvas.drawRRect(ellipseRect, paint)
            canvas.restore()
        }

        // Super-skinny
        for (i in paints.indices) {
            val rect = Rect.fromLTRB(0f, -60f, 1f, 60f)
            val circleRect = RRect(rect, 5f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 3.25f + 0.25f, kYStart + kYStep * i + 0.75f)
            val paint = paints[i](rand).copy(color = genColor(rand))
            canvas.drawRRect(circleRect, paint)
            canvas.restore()
        }

        // Super-short
        for (i in paints.indices) {
            val rect = Rect.fromLTRB(-80f, -1f, 80f, 0f)
            val circleRect = RRect(rect, 5f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 2.5f + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
            val paint = paints[i](rand).copy(color = genColor(rand))
            canvas.drawRRect(circleRect, paint)
            canvas.restore()
        }

        // Radial-gradient row
        val gradient = Shader.RadialGradient(
            center = Point(0f, 0f), radius = 20f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(0f, 0f, 1f)),
                GradientStop(0.5f, Color.fromRGBA(1f, 0f, 0f)),
                GradientStop(1f, Color.fromRGBA(0f, 1f, 0f)),
            ),
            tileMode = TileMode.CLAMP,
        )
        for (i in paints.indices) {
            canvas.save()
            canvas.translate(kXStart + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
            val paint = paints[i](rand).copy(color = genColor(rand), shader = gradient)
            canvas.drawRect(kRect, rectPaint)
            canvas.drawRRect(circleRRect, paint)
            canvas.restore()
        }

        // Strokes-and-radii column
        run {
            val radii = listOf(
                Pair(10f, 10f), Pair(5f, 15f), Pair(5f, 15f), Pair(5f, 15f),
            )
            val strokeWidths = floatArrayOf(20f, 10f, 20f, 40f)
            for (i in 0 until 4) {
                val (rx, ry) = radii[i]
                val circleRect = RRect(kRect, CornerRadii(rx, ry), CornerRadii(rx, ry), CornerRadii(rx, ry), CornerRadii(rx, ry))
                canvas.save()
                canvas.translate(kXStart + kXStep * 5 + 0.25f, kYStart + kYStep * i + 0.75f + 0.5f * kYStep)
                val color = genColor(rand)
                val p = Paint(antiAlias = true, style = PaintStyle.STROKE, strokeWidth = strokeWidths[i], color = color)
                canvas.drawRRect(circleRect, p)
                canvas.restore()
            }
        }

        // OOO rect via drawRoundRect (RRect with ooo rect)
        run {
            canvas.save()
            canvas.translate(kXStart + kXStep * 5 + 0.25f, kYStart + kYStep * 4 + 0.25f + 0.5f * kYStep)
            val color = genColor(rand)
            val p = Paint(color = color)
            val oooRect = Rect.fromLTRB(20f, 30f, -20f, -30f)
            canvas.drawRRect(RRect(oooRect, 10f), p)
            canvas.restore()
        }

        // RRect with stroke > radius/2
        run {
            val smallRect = Rect.fromLTRB(-30f, -20f, 30f, 20f)
            val circleRect = RRect(smallRect, 5f)
            canvas.save()
            canvas.translate(kXStart + kXStep * 5 + 0.25f, kYStart - kYStep + 73f / 4f + 0.5f * kYStep)
            val color = genColor(rand)
            val p = Paint(antiAlias = true, style = PaintStyle.STROKE, strokeWidth = 25f, color = color)
            canvas.drawRRect(circleRect, p)
            canvas.restore()
        }
    }

    private fun genColor(rand: Random): Color {
        val h = rand.nextFloat() * 360f
        val s = rand.nextFloat() * 0.25f + 0.75f
        val v = rand.nextFloat() * 0.25f + 0.75f
        return hsvToColor(h, s, v)
    }

    private fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val hi = (h / 60f).toInt() % 6
        val f = h / 60f - hi
        val p = v * (1f - s)
        val q = v * (1f - f * s)
        val t = v * (1f - (1f - f) * s)
        val (r, g, bl) = when (hi) {
            0 -> Triple(v, t, p); 1 -> Triple(q, v, p)
            2 -> Triple(p, v, t); 3 -> Triple(p, q, v)
            4 -> Triple(t, p, v); else -> Triple(v, p, q)
        }
        return Color.fromRGBA(r, g, bl)
    }
}
