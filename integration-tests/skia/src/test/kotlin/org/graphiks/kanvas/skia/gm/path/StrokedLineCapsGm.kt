package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import kotlin.math.max

/**
 * Port of Skia's `gm/strokedlines.cpp` (strokedline_caps).
 * Stroke caps (square, butt, round) across various line lengths.
 * @see https://github.com/google/skia/blob/main/gm/strokedlines.cpp
 */
class StrokedLineCapsGm : SkiaGm {
    override val name = "strokedline_caps"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 92.0
    override val width = 1400
    override val height = 740

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(kStrokeWidth * 3f / 2f, kStrokeWidth * 3f / 2f)

        val caps = listOf(StrokeCap.SQUARE, StrokeCap.BUTT, StrokeCap.ROUND)
        val lengths = floatArrayOf(4f * kStrokeWidth, kStrokeWidth, kStrokeWidth / 2f, kStrokeWidth / 4f)

        val gradient = Shader.LinearGradient(
            start = Point(-kStrokeWidth, -kStrokeWidth),
            end = Point(2f * kStrokeWidth, 4f * kStrokeWidth),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.75f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(1f, Color.BLUE),
            ),
            tileMode = TileMode.CLAMP,
        )

        for (i in caps.indices) {
            canvas.save()
            for (j in lengths.indices) {
                val l = lengths[j]
                val paint = Paint(
                    style = PaintStyle.STROKE,
                    strokeWidth = kStrokeWidth,
                    strokeCap = caps[i],
                    shader = gradient,
                    antiAlias = true,
                )
                canvas.drawLine(0f, 0f, l, l, paint)
                canvas.translate(max(l, 0f) + 2f * kStrokeWidth, 0f)
            }
            canvas.restore()
            canvas.translate(0f, lengths[0] + 2f * kStrokeWidth)
        }
    }

    private companion object {
        const val kStrokeWidth: Float = 20f
    }
}
