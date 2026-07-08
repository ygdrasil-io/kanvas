package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.math.max

/**
 * Port of Skia's `gm/shadowutils.cpp` — occlusion variant.
 * Tests shadow utility with occlusion (transparency) for convex and concave paths.
 * @see https://github.com/google/skia/blob/main/gm/shadowutils.cpp
 */
class ShadowUtilsOcclGm : SkiaGm {
    override val name = "shadow_utils_occl"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 960

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paths = listOf(
            Path { }.also { it.addRRect(RRect(Rect(0f, 0f, 50f, 50f), CornerRadii(10f, 10f))) },
            Path { }.also { it.addRect(Rect(0f, 0f, 50f, 50f)) },
            Path { }.also { it.addCircle(25f, 25f, 25f) },
            Path { }.also { it.addOval(Rect(0f, 0f, 20f, 60f)) },
        )

        val concavePaths = listOf(
            Path {
                moveTo(0.0f, -33.3333f)
                lineTo(9.62f, -16.6667f)
                lineTo(28.867f, -16.6667f)
                lineTo(19.24f, 0.0f)
                lineTo(28.867f, 16.6667f)
                lineTo(9.62f, 16.6667f)
                lineTo(0.0f, 33.3333f)
                lineTo(-9.62f, 16.6667f)
                lineTo(-28.867f, 16.6667f)
                lineTo(-19.24f, 0.0f)
                lineTo(-28.867f, -16.6667f)
                lineTo(-9.62f, -16.6667f)
                close()
            },
            Path {
                moveTo(50f, 0f)
                cubicTo(100f, 25f, 60f, 50f, 50f, 0f)
                cubicTo(0f, -25f, 40f, -50f, 50f, 0f)
            },
        )

        val kPad = 15f
        val kHeight = 50f

        canvas.translate(3f * kPad, 3f * kPad)
        canvas.save()
        var x = 0f
        var dy = 0f

        val fillPaint = Paint(
            color = Color.fromRGBA(0xBF / 255f, 0xBF / 255f, 0xBF / 255f, 1f),
            style = PaintStyle.FILL,
            antiAlias = true,
        )
        val fillPaint500 = Paint(
            color = Color.fromRGBA(0xBF / 255f, 0xBF / 255f, 0xBF / 255f, 0.5f),
            style = PaintStyle.FILL,
            antiAlias = true,
        )

        var pathCounter = 0
        for (path in paths) {
            val postM = Rect(0f, 0f, 50f, 50f)
            val w = postM.width + kHeight
            val dx = w + kPad
            if (x + dx > width - 3f * kPad) {
                canvas.restore()
                canvas.translate(0f, dy)
                canvas.save()
                x = 0f
                dy = 0f
                pathCounter = 0
            }
            canvas.save()
            val transparent = pathCounter % 3 == 0
            val p = if (transparent) fillPaint500 else fillPaint
            canvas.drawPath(path, p)
            canvas.restore()
            canvas.translate(dx, 0f)
            x += dx
            dy = max(dy, postM.height + kPad + kHeight)
            ++pathCounter
        }

        canvas.restore()
        canvas.translate(kPad, dy)
        canvas.save()
        x = kPad
        dy = 0f
        for (path in concavePaths) {
            val postM = Rect(0f, 0f, 50f, 50f)
            val w = postM.width + kHeight
            val dx = w + kPad
            canvas.save()
            canvas.drawPath(path, fillPaint)
            canvas.restore()
            canvas.translate(dx, 0f)
            x += dx
            dy = max(dy, postM.height + kPad + kHeight)
        }
        canvas.restore()
    }
}
