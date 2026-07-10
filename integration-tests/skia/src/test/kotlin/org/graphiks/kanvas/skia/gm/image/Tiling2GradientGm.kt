package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/tilemodes.cpp::Tiling2GM` (gradient variant).
 * Tests tile modes with LinearGradient, RadialGradient, and SweepGradient.
 * @see https://github.com/google/skia/blob/main/gm/tilemodes.cpp
 */
class Tiling2GradientGm : SkiaGm {
    override val name = "tilemode_gradient"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 610

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(3f / 2f, 3f / 2f)

        val w = 32f
        val h = 32f
        val r = Rect(-w, -h, w * 2f, h * 2f)

        val modes = listOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR)
        val modeNames = listOf("Clamp", "Repeat", "Mirror")

        val font = Font(
            typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
            size = 12f,
        )
        val textPaint = Paint()

        var y = 24f
        var x = 66f
        for (kx in modes.indices) {
            val tw = font.measureText(modeNames[kx])
            val tx = x + r.width / 2f - tw / 2f
            canvas.drawString(modeNames[kx], tx, y, font, textPaint)
            x += r.width * 4f / 3f
        }

        y += 16f + h

        val colors = listOf(Color.RED, Color.fromRGBA(0f, 68f / 255f, 1f))

        for (ky in modes.indices) {
            x = 16f + w
            val tw = font.measureText(modeNames[ky])
            canvas.drawString(modeNames[ky], x - tw - 5f, y + h / 2f + 4f, font, textPaint)
            x += 50f

            for (kx in modes.indices) {
                val stops = listOf(
                    GradientStop(0f, colors[0]),
                    GradientStop(1f, colors[1]),
                )
                val kyMode = modes[ky]
                val shader = when (kyMode) {
                    TileMode.CLAMP -> Shader.LinearGradient(
                        start = Point(0f, 0f),
                        end = Point(w, h),
                        stops = stops,
                        tileMode = modes[kx],
                    )
                    TileMode.REPEAT -> Shader.RadialGradient(
                        center = Point(w / 2f, h / 2f),
                        radius = w / 2f,
                        stops = stops,
                        tileMode = modes[kx],
                    )
                    TileMode.MIRROR -> Shader.SweepGradient(
                        center = Point(w / 2f, h / 2f),
                        startAngle = 135f,
                        endAngle = 225f,
                        stops = stops,
                        tileMode = modes[kx],
                    )
                    TileMode.DECAL -> error("unreachable")
                }
                val paint = Paint(shader = shader)
                canvas.save()
                canvas.translate(x, y)
                canvas.drawRect(r, paint)
                canvas.restore()
                x += r.width * 4f / 3f
            }
            y += r.height * 4f / 3f
        }
    }
}
