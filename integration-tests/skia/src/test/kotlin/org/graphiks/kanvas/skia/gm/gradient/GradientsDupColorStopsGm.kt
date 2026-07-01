package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients.cpp::gradients_dup_color_stops`.
 * Exercises duplicate color stops at start, end, and middle positions
 * across linear, radial, conical, and sweep gradient types.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsDupColorStopsGm : SkiaGm {
    override val name = "gradients_dup_color_stops"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 704
    override val height = 564

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val preColor = Color.RED
        val postColor = Color.BLUE
        val color0 = Color.BLACK
        val color1 = Color.GREEN

        val runs = listOf(
            listOf(GradientStop(0f, color0), GradientStop(1f, color1)),
            listOf(GradientStop(0f, preColor), GradientStop(0f, color0), GradientStop(1f, color1)),
            listOf(GradientStop(0f, color0), GradientStop(1f, color1), GradientStop(1f, postColor)),
            listOf(GradientStop(0f, preColor), GradientStop(0f, color0), GradientStop(1f, color1), GradientStop(1f, postColor)),
            listOf(GradientStop(0f, color0), GradientStop(0.5f, color0), GradientStop(0.5f, color1), GradientStop(1f, color1)),
        )

        val factories: List<(List<GradientStop>) -> Shader> = listOf(
            { stops -> Shader.LinearGradient(start = Point(30f, 30f), end = Point(SIZE - 30f, SIZE - 30f), stops = stops, tileMode = TileMode.CLAMP) },
            { stops -> Shader.RadialGradient(center = Point(half, half), radius = half - 10f, stops = stops, tileMode = TileMode.CLAMP) },
            { stops -> Shader.ConicalGradient(start = Point(half, half), startRadius = 20f, end = Point(half, half), endRadius = half - 10f, stops = stops, tileMode = TileMode.CLAMP) },
            { stops -> Shader.SweepGradient(center = Point(half, half), stops = stops, tileMode = TileMode.CLAMP) },
        )

        val rect = Rect.fromXYWH(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        val dx = SIZE + 20f
        val dy = SIZE + 20f

        canvas.translate(10f, 10f - dy)
        for (factory in factories) {
            canvas.translate(0f, dy)
            canvas.save()
            for (run in runs) {
                canvas.drawRect(rect, Paint(shader = factory(run)))
                canvas.translate(dx, 0f)
            }
            canvas.restore()
        }
    }

    private companion object {
        const val SIZE = 121
        val half = SIZE * 0.5f
    }
}
