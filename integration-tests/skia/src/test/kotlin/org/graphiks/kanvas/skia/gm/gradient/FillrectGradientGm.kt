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
 * Port of Skia's `gm/fillrect_gradient.cpp`.
 * A 2-column x 9-row grid of gradient rects testing various stop configurations.
 * @see https://github.com/google/skia/blob/main/gm/fillrect_gradient.cpp
 */
class FillrectGradientGm : SkiaGm {
    override val name = "fillrect_gradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 28.6
    override val width = kNumColumns * (kCellSize + kPadSize)
    override val height = kNumRows * (kCellSize + kPadSize)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawGradient(canvas, listOf(GradientStop(0f, Color.GREEN), GradientStop(1f, Color.WHITE)))
        drawGradient(canvas, listOf(GradientStop(0f, Color.GREEN), GradientStop(0.5f, Color.WHITE), GradientStop(1f, Color.RED)))
        drawGradient(canvas, listOf(GradientStop(0.4f, Color.GREEN), GradientStop(0.5f, Color.WHITE), GradientStop(0.6f, Color.RED)))
        drawGradient(canvas, listOf(GradientStop(0f, Color.RED)))
        drawGradient(canvas, listOf(GradientStop(1f, Color.RED)))
        drawGradient(canvas, listOf(GradientStop(0.5f, Color.RED)))
        drawGradient(canvas, listOf(
            GradientStop(0f, Color.BLUE), GradientStop(0.5f, Color.WHITE),
            GradientStop(0.5f, Color.RED), GradientStop(1f, Color.fromRGBA(1f, 1f, 0f)),
        ))
        drawGradient(canvas, listOf(
            GradientStop(0f, Color.BLUE), GradientStop(0.5f, Color.WHITE),
            GradientStop(0.5f, Color.fromRGBA(0x88f / 255f, 0x88f / 255f, 0x88f / 255f)),
            GradientStop(0.5f, Color.fromRGBA(0f, 1f, 1f)),
            GradientStop(0.5f, Color.RED), GradientStop(1f, Color.fromRGBA(1f, 1f, 0f)),
        ))
        drawGradient(canvas, listOf(
            GradientStop(0.5f, Color.WHITE), GradientStop(0.5f, Color.fromRGBA(0x88f / 255f, 0x88f / 255f, 0x88f / 255f)),
            GradientStop(1f, Color.fromRGBA(1f, 1f, 0f)), GradientStop(0.5f, Color.fromRGBA(0f, 1f, 1f)),
            GradientStop(0.5f, Color.RED), GradientStop(0f, Color.BLUE),
        ))
    }

    private fun drawGradient(canvas: GmCanvas, stops: List<GradientStop>) {
        val cellRect = Rect.fromXYWH(0f, 0f, kCellSize.toFloat(), kCellSize.toFloat())

        val linear = Shader.LinearGradient(
            start = Point(kCellSize.toFloat(), 0f),
            end = Point(kCellSize.toFloat(), kCellSize.toFloat()),
            stops = stops, tileMode = TileMode.CLAMP,
        )
        var paint = Paint(antiAlias = true, shader = linear)
        canvas.drawRect(cellRect, paint)

        canvas.save()
        canvas.translate((kCellSize + kPadSize).toFloat(), 0f)

        val radial = Shader.RadialGradient(
            center = Point(kCellSize / 2f, kCellSize / 2f),
            radius = kCellSize / 2f,
            stops = stops, tileMode = TileMode.CLAMP,
        )
        paint = paint.copy(shader = radial)
        canvas.drawRect(cellRect, paint)

        canvas.restore()
        canvas.translate(0f, (kCellSize + kPadSize).toFloat())
    }

    private companion object {
        const val kCellSize: Int = 50
        const val kNumColumns: Int = 2
        const val kNumRows: Int = 9
        const val kPadSize: Int = 10
    }
}
