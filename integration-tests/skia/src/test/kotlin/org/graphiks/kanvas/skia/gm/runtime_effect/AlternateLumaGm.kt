package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.GChannelSplatWgsl
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/alternateluma.cpp` (200 x 200).
 *
 * Draws a color gradient through a G-channel-splat color filter
 * wrapped in a working-color-space transform (SRGB->Linear, apply,
 * Linear->SRGB).
 *
 * @see https://github.com/google/skia/blob/main/gm/alternateluma.cpp
 */
class AlternateLumaGm : SkiaGm {
    override val name = "AlternateLuma"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(GChannelSplatWgsl).getOrThrow()
        val gChannelSplat = effect.makeColorFilter(UniformBlock {})

        val workingFilter = ColorFilter.Compose(
            ColorFilter.LinearToSRGB,
            ColorFilter.Compose(gChannelSplat, ColorFilter.SRGBToLinear),
        )

        val gradient = Shader.LinearGradient(
            Point(0f, 0f), Point(width.toFloat(), height.toFloat()),
            listOf(
                GradientStop(0f, Color(0xFFFF0000u)),
                GradientStop(0.25f, Color(0xFF00FF00u)),
                GradientStop(0.5f, Color(0xFF0000FFu)),
                GradientStop(0.75f, Color(0xFFFF00FFu)),
                GradientStop(1f, Color(0xFFFFFF00u)),
            ),
        )

        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(shader = gradient, colorFilter = workingFilter),
        )
    }
}
