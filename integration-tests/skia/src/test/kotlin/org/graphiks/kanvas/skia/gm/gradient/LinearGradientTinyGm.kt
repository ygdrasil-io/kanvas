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
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradients_no_texture.cpp` (LinearGradientTinyGM).
 * Stress-tests degenerate stop positions and near-collinear endpoints.
 * @see https://github.com/google/skia/blob/main/gm/gradients_no_texture.cpp
 */
class LinearGradientTinyGm : SkiaGm {
    override val name = "linear_gradient_tiny"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 55.0
    override val width = 600
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kRectSize = 100f
        val green = Color.fromRGBA(0f, 1f, 0f, 1f)
        val red = Color.fromRGBA(1f, 0f, 0f, 1f)

        data class Config(val p0: Point, val p1: Point, val pos: FloatArray)
        val configs = listOf(
            Config(Point(0f, 0f), Point(10f, 0f),       floatArrayOf(0f, 0.999999f,    1f)),
            Config(Point(0f, 0f), Point(10f, 0f),       floatArrayOf(0f, 0.000001f,    1f)),
            Config(Point(0f, 0f), Point(10f, 0f),       floatArrayOf(0f, 0.999999999f, 1f)),
            Config(Point(0f, 0f), Point(10f, 0f),       floatArrayOf(0f, 0.000000001f, 1f)),

            Config(Point(0f, 0f), Point(0f, 10f),       floatArrayOf(0f, 0.999999f,    1f)),
            Config(Point(0f, 0f), Point(0f, 10f),       floatArrayOf(0f, 0.000001f,    1f)),
            Config(Point(0f, 0f), Point(0f, 10f),       floatArrayOf(0f, 0.999999999f, 1f)),
            Config(Point(0f, 0f), Point(0f, 10f),       floatArrayOf(0f, 0.000000001f, 1f)),

            Config(Point(0f, 0f),       Point(0.00001f, 0f), floatArrayOf(0f, 0.5f, 1f)),
            Config(Point(9.99999f, 0f), Point(10f, 0f),      floatArrayOf(0f, 0.5f, 1f)),
            Config(Point(0f, 0f),       Point(0f, 0.00001f), floatArrayOf(0f, 0.5f, 1f)),
            Config(Point(0f, 9.99999f), Point(0f, 10f),      floatArrayOf(0f, 0.5f, 1f)),
        )

        for (i in configs.indices) {
            val cfg = configs[i]
            val stops = listOf(
                GradientStop(cfg.pos[0], green),
                GradientStop(cfg.pos[1], red),
                GradientStop(cfg.pos[2], green),
            )
            val paint = Paint(shader = Shader.LinearGradient(
                start = cfg.p0, end = cfg.p1,
                stops = stops, tileMode = TileMode.CLAMP,
            ))
            canvas.save()
            canvas.translate(
                kRectSize * ((i % 4) * 1.5f + 0.25f),
                kRectSize * ((i / 4) * 1.5f + 0.25f),
            )
            canvas.drawRect(Rect(0f, 0f, kRectSize, kRectSize), paint)
            canvas.restore()
        }
    }
}
