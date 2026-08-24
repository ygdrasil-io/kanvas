package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/gradient_dirty_laundry.cpp`.
 * 40-stop RGBWB gradient sequence through linear, radial and sweep gradients.
 * @see https://github.com/google/skia/blob/main/gm/gradient_dirty_laundry.cpp
 */
class GradientDirtyLaundryGm : SkiaGm {
    override val name = "gradient_dirty_laundry"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 615

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            RectF32(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = ColorARGB.fromRGBA(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)),
        )

        val basePattern = listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue, ColorARGB.White, ColorARGB.Black)
        val colors = List(40) { basePattern[it % basePattern.size] }
        val stops = colors.indices.map { i ->
            GradientStop(i.toFloat() / (colors.size - 1), colors[i])
        }
        val r = RectF32.ofLTRB(0f, 0f, 100f, 100f)

        canvas.translate(20f, 20f)
        canvas.save()

        val linearPaint = Paint(shader = Shader.LinearGradient(
            start = Point2F32(0f, 0f), end = Point2F32(100f, 100f),
            stops = stops, tileMode = TileMode.CLAMP,
        ))
        canvas.drawRect(r, linearPaint)
        canvas.translate(0f, 120f)

        val cx = 50f
        val cy = 50f
        val radialPaint = Paint(shader = Shader.RadialGradient(
            center = Point2F32(cx, cy), radius = cx,
            stops = stops, tileMode = TileMode.CLAMP,
        ))
        canvas.drawRect(r, radialPaint)
        canvas.translate(0f, 120f)

        val sweepPaint = Paint(shader = Shader.SweepGradient(
            center = Point2F32(cx, cy),
            stops = stops, tileMode = TileMode.CLAMP,
        ))
        canvas.drawRect(r, sweepPaint)

        canvas.restore()
    }
}
