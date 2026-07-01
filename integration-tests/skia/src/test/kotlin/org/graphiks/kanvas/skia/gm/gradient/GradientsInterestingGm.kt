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
 * Port of Skia's `gm/gradients.cpp::gradients_interesting`.
 * Exercises special-case gradient effects with 6 color/position configs
 * x 3 tile modes in a grid.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsInterestingGm : SkiaGm {
    override val name = "gradients_interesting"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 10.8
    override val width = 640
    override val height = 1300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val yellow = Color.fromRGBA(1f, 1f, 0f)
        val colors2 = listOf(Color.RED, Color.BLUE)
        val colors3 = listOf(Color.RED, yellow, Color.BLUE)
        val colors4 = listOf(Color.RED, yellow, yellow, Color.BLUE)

        val configs = listOf(
            colors2.zip(listOf(0f, 1f)) { c, p -> GradientStop(p, c) },
            colors3.zip(listOf(0f, 0.5f, 1f)) { c, p -> GradientStop(p, c) },
            colors3.zip(listOf(0f, 0.999f, 1f)) { c, p -> GradientStop(p, c) },
            colors3.zip(listOf(0f, 0f, 1f)) { c, p -> GradientStop(p, c) },
            colors3.zip(listOf(0f, 1f, 1f)) { c, p -> GradientStop(p, c) },
            colors4.zip(listOf(0f, 0.5f, 0.5f, 1f)) { c, p -> GradientStop(p, c) },
        )

        val modes = listOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR)

        val cellSize = 200f
        val p0 = Point(cellSize / 3f, cellSize / 3f)
        val p1 = Point(cellSize * 2f / 3f, cellSize * 2f / 3f)

        for (cfg in configs) {
            canvas.save()
            for (mode in modes) {
                val shader = Shader.LinearGradient(start = p0, end = p1, stops = cfg, tileMode = mode)
                canvas.drawRect(Rect.fromXYWH(0f, 0f, cellSize, cellSize), Paint(shader = shader))
                canvas.translate(cellSize * 1.1f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, cellSize * 1.1f)
        }
    }
}
