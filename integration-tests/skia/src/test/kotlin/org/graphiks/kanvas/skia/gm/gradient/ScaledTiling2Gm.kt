package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/tilemodes_scaled.cpp::ScaledTiling2GM`.
 * 3 x 3 grid of rects under scale(1.5, 1.5) with gradient shaders using
 * different (tmx, tmy) permutations of (Clamp, Repeat, Mirror).
 * @see https://github.com/google/skia/blob/main/gm/tilemodes_scaled.cpp
 */
class ScaledTiling2Gm : SkiaGm {
    override val name = "scaled_tilemode_gradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 610

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(3f / 2f, 3f / 2f)

        val w = G_WIDTH.toFloat()
        val h = G_HEIGHT.toFloat()
        val r = Rect.fromLTRB(-w, -h, w * 2, h * 2)

        val modes = arrayOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR)

        var y = 24f + 16f + h
        for (ky in modes.indices) {
            var x = 16f + w + 50f
            for (kx in modes.indices) {
                val paint = Paint(shader = makeGrad(modes[kx], modes[ky]))
                canvas.save()
                canvas.translate(x, y)
                canvas.drawRect(r, paint)
                canvas.restore()
                x += r.width * 4f / 3f
            }
            y += r.height * 4f / 3f
        }
    }

    private fun makeGrad(tx: TileMode, ty: TileMode): Shader {
        val pts = arrayOf(Point(0f, 0f), Point(G_WIDTH.toFloat(), G_HEIGHT.toFloat()))
        val center = Point(G_WIDTH / 2f, G_HEIGHT / 2f)
        val rad = G_WIDTH / 2f
        val stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(1f, Color.fromRGBA(0f, 68f / 255f, 1f, 1f)),
        )
        return when (ty.ordinal % 3) {
            0 -> Shader.LinearGradient(start = pts[0], end = pts[1], stops = stops, tileMode = tx)
            1 -> Shader.RadialGradient(center = center, radius = rad.toFloat(), stops = stops, tileMode = tx)
            else -> Shader.SweepGradient(center = center, startAngle = 135f, endAngle = 225f, stops = stops, tileMode = tx)
        }
    }

    private companion object {
        const val G_WIDTH: Int = 32
        const val G_HEIGHT: Int = 32
    }
}
