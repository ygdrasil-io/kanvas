package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gradients.cpp::GradientsViewPerspectiveGM`.
 * Applies a perspective transform to the view matrix before drawing
 * the standard 6x5 gradient grid.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsViewPerspectiveGm : SkiaGm {
    override val name = "gradients_view_perspective"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 840
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)

        val perspective = Matrix33.makeAll(
            sx = 1f, kx = 8f / 25f, tx = 0f,
            ky = 0f, sy = 1f, ty = 0f,
            p0 = 0f, p1 = 0.001f, p2 = 1f,
        )
        canvas.concat(perspective)

        val pts = arrayOf(Point(0f, 0f), Point(100f, 100f))
        val rect = Rect(0f, 0f, 100f, 100f)

        val base5 = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK)
        val gradDatas = listOf(
            listOf(GradientStop(0f, base5[0]), GradientStop(1f, base5[1])),
            listOf(GradientStop(0f, base5[0]), GradientStop(1f, base5[1])),
            listOf(GradientStop(0.25f, base5[0]), GradientStop(0.75f, base5[1])),
            base5.mapIndexed { i, c -> GradientStop(i.toFloat() / (base5.size - 1), c) },
            listOf(
                GradientStop(0f, base5[0]),
                GradientStop(0.125f, base5[1]),
                GradientStop(0.5f, base5[2]),
                GradientStop(0.875f, base5[3]),
                GradientStop(1f, base5[4]),
            ),
            listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0f, Color.GREEN),
                GradientStop(1f, Color.GREEN),
                GradientStop(1f, Color.BLUE),
            ),
        )

        val makers: List<(Array<Point>, List<GradientStop>) -> Shader> = listOf(
            { p, stops ->
                Shader.LinearGradient(start = p[0], end = p[1], stops = stops, tileMode = TileMode.CLAMP)
            },
            { p, stops ->
                val cx = (p[0].x + p[1].x) * 0.5f; val cy = (p[0].y + p[1].y) * 0.5f
                Shader.RadialGradient(center = Point(cx, cy), radius = cx, stops = stops, tileMode = TileMode.CLAMP)
            },
            { p, stops ->
                val cx = (p[0].x + p[1].x) * 0.5f; val cy = (p[0].y + p[1].y) * 0.5f
                Shader.SweepGradient(center = Point(cx, cy), stops = stops, tileMode = TileMode.CLAMP)
            },
            { p, stops ->
                val cx = (p[0].x + p[1].x) * 0.5f; val cy = (p[0].y + p[1].y) * 0.5f
                val c0 = Point(cx, cy)
                val c1 = Point(p[0].x + 0.6f * (p[1].x - p[0].x), p[0].y + 0.25f * (p[1].y - p[0].y))
                Shader.ConicalGradient(
                    start = c1, startRadius = (p[1].x - p[0].x) / 7f,
                    end = c0, endRadius = (p[1].x - p[0].x) / 2f,
                    stops = stops, tileMode = TileMode.CLAMP,
                )
            },
            { p, stops ->
                val r0 = (p[1].x - p[0].x) / 10f
                val r1 = (p[1].x - p[0].x) / 3f
                val c0 = Point(p[0].x + r0, p[0].y + r0)
                val c1 = Point(p[1].x - r1, p[1].y - r1)
                Shader.ConicalGradient(
                    start = c1, startRadius = r1,
                    end = c0, endRadius = r0,
                    stops = stops, tileMode = TileMode.CLAMP,
                )
            },
        )

        canvas.translate(20f, 20f)
        for (i in gradDatas.indices) {
            canvas.save()
            val matrix = if (i == 5) {
                Matrix33.scale(0.5f, 0.5f) * Matrix33.translate(25f, 25f)
            } else null
            for (j in makers.indices) {
                val shader = makers[j](pts, gradDatas[i])
                val finalShader = if (matrix != null) Shader.WithLocalMatrix(shader, matrix) else shader
                canvas.drawRect(rect, Paint(antiAlias = true, shader = finalShader))
                canvas.translate(0f, 120f)
            }
            canvas.restore()
            canvas.translate(120f, 0f)
        }
    }
}
