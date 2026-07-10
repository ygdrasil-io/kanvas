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
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/gradients.cpp::sweep_tiling`.
 * 3 rows × 4 columns of 160 × 160 rects, each painted with a sweep
 * gradient centred in the cell. Rows = {Clamp, Repeat, Mirror},
 * columns = four (startAngle, endAngle) pairs.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class SweepTilingGm : SkiaGm {
    override val name = "sweep_tiling"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 10.4
    override val width = 690
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val stops = listOf(
            GradientStop(0f, Color.BLUE),
            GradientStop(0.25f, Color.fromRGBA(1f, 1f, 0f, 1f)),
            GradientStop(0.50f, Color.GREEN),
        )

        val modes = arrayOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR)

        val angles = arrayOf(
            -330f to -270f,
            30f to 90f,
            390f to 450f,
            -30f to 800f,
        )

        val r = Rect.fromXYWH(0f, 0f, SIZE, SIZE)

        for (mode in modes) {
            canvas.save()
            for ((start, end) in angles) {
                val shader = Shader.SweepGradient(
                    center = Point(SIZE / 2f, SIZE / 2f),
                    startAngle = start,
                    endAngle = end,
                    stops = stops,
                    tileMode = mode,
                )
                canvas.drawRect(r, Paint(shader = shader))
                canvas.translate(SIZE * 1.1f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, SIZE * 1.1f)
        }
    }

    private companion object {
        const val SIZE: Float = 160f
    }
}
