package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/transparency.cpp`
 * (`DEF_SIMPLE_GM(transparency_check, canvas, 1792, 1080)`).
 * Verifies that a transparent bitmap drawn over a checkerboard looks correct.
 * @see https://github.com/google/skia/blob/main/gm/transparency.cpp
 */
class TransparencyCheckGm : SkiaGm {
    override val name = "transparency_check"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1792
    override val height = 1080

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Checkerboard background
        drawCheckerboard(canvas, ColorARGB.fromRGBA(0.6f, 0.6f, 0.6f), ColorARGB.fromRGBA(0.4f, 0.4f, 0.4f), 8)

        canvas.save()
        val kColors = listOf(
            ColorARGB.Black,
            ColorARGB.fromRGBA(0.5f, 0.5f, 0.5f),
            ColorARGB.White,
            ColorARGB.Red,
            ColorARGB.fromRGBA(1f, 1f, 0f),
            ColorARGB.Green,
            ColorARGB.fromRGBA(0f, 1f, 1f),
            ColorARGB.Blue,
            ColorARGB.fromRGBA(1f, 0f, 1f),
        )
        val rowHeight = 9f / kColors.size
        for (i in kColors.indices) {
            val shader = Shader.LinearGradient(
                start = Point2F32(0f, 0f), end = Point2F32(256f, 0f),
                stops = listOf(
                    GradientStop(0f, ColorARGB.Transparent),
                    GradientStop(1f, kColors[i]),
                ),
                tileMode = TileMode.CLAMP,
            )
            canvas.save()
            canvas.scale(7f, 120f)
            canvas.drawRect(
                RectF32.ofOriginSize(0f, i * rowHeight, 256f, rowHeight),
                Paint(shader = shader),
            )
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawCheckerboard(canvas: GmCanvas, c0: ColorARGB, c1: ColorARGB, size: Int) {
        val w = canvas.width; val h = canvas.height
        for (y in 0 until h step size) {
            for (x in 0 until w step size) {
                val on = ((x / size) + (y / size)) % 2 == 0
                canvas.drawRect(
                    RectF32.ofOriginSize(x.toFloat(), y.toFloat(), size.toFloat(), size.toFloat()),
                    Paint(color = if (on) c0 else c1),
                )
            }
        }
    }
}
