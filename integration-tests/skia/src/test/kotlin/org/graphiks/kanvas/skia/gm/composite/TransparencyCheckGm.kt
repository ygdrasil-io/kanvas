package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/transparency.cpp`
 * (`DEF_SIMPLE_GM(transparency_check, canvas, 1792, 1080)`).
 * Verifies that a transparent bitmap drawn over a checkerboard looks correct.
 * @see https://github.com/google/skia/blob/main/gm/transparency.cpp
 */
class TransparencyCheckGm : SkiaGm {
    override val name = "transparency_check"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 1792
    override val height = 1080

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Checkerboard background
        drawCheckerboard(canvas, Color.fromRGBA(0.6f, 0.6f, 0.6f), Color.fromRGBA(0.4f, 0.4f, 0.4f), 8)

        canvas.save()
        val kColors = listOf(
            Color.BLACK,
            Color.fromRGBA(0.5f, 0.5f, 0.5f),
            Color.WHITE,
            Color.RED,
            Color.fromRGBA(1f, 1f, 0f),
            Color.GREEN,
            Color.fromRGBA(0f, 1f, 1f),
            Color.BLUE,
            Color.fromRGBA(1f, 0f, 1f),
        )
        val rowHeight = 9f / kColors.size
        for (i in kColors.indices) {
            val shader = Shader.LinearGradient(
                start = Point(0f, 0f), end = Point(256f, 0f),
                stops = listOf(
                    GradientStop(0f, Color.TRANSPARENT),
                    GradientStop(1f, kColors[i]),
                ),
                tileMode = TileMode.CLAMP,
            )
            canvas.save()
            canvas.scale(7f, 120f)
            canvas.drawRect(
                Rect.fromXYWH(0f, i * rowHeight, 256f, rowHeight),
                Paint(shader = shader),
            )
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawCheckerboard(canvas: GmCanvas, c0: Color, c1: Color, size: Int) {
        val w = canvas.width; val h = canvas.height
        for (y in 0 until h step size) {
            for (x in 0 until w step size) {
                val on = ((x / size) + (y / size)) % 2 == 0
                canvas.drawRect(
                    Rect.fromXYWH(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat()),
                    Paint(color = if (on) c0 else c1),
                )
            }
        }
    }
}
