package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.ColorSpaceInterpolation
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
 * Port of Skia's `gm/gradients.cpp` — OKLCH tilemode slice.
 * 4 horizontal strips, one per tile mode, showing a blue→yellow
 * gradient drawn with OKLCH color space interpolation.
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class GradientsColorSpaceTilemodeGm : SkiaGm {
    override val name = "gradients_color_space_tilemode"
    override val renderFamily = RenderFamily.GRADIENT
    override val minSimilarity = 0.0
    override val width = 360
    override val height = 105

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(0x88 / 255f, 0x88 / 255f, 0x88 / 255f, 1f))
        )

        val tileModes = listOf(
            TileMode.CLAMP,
            TileMode.REPEAT,
            TileMode.MIRROR,
            TileMode.DECAL,
        )

        for ((i, tileMode) in tileModes.withIndex()) {
            val paint = Paint(
                shader = Shader.LinearGradient(
                    start = Point(20f, 0f), end = Point(120f, 0f),
                    stops = listOf(
                        GradientStop(0f, Color.BLUE),
                        GradientStop(1f, Color.fromRGBA(1f, 1f, 0f, 1f)),
                    ),
                    tileMode = tileMode,
                    interpolation = ColorSpaceInterpolation.OKLCH,
                )
            )
            canvas.drawRect(Rect.fromXYWH(5f, 5f + i * 25f, 350f, 20f), paint)
        }
    }
}
