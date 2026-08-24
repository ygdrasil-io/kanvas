package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.ThresholdRTWgsl
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/runtimeshader.cpp::ThresholdRT` (512 x 512).
 *
 * Draws variants of a smooth-threshold blend using three child
 * gradient shaders and configurable cutoff/slope uniforms.
 *
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class ThresholdRTGm : SkiaGm {
    override val name = "threshold_rt"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.006103515625
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(ThresholdRTWgsl).getOrThrow()
        val w0 = width
        val h0 = height

        val beforeGrad = Shader.LinearGradient(
            Point2F32(0f, 0f), Point2F32(w0.toFloat(), h0.toFloat()),
            listOf(
                GradientStop(0f, ColorARGB.Red),
                GradientStop(0.5f, ColorARGB.Green),
                GradientStop(1f, ColorARGB.Blue),
            ),
        )
        val afterGrad = Shader.LinearGradient(
            Point2F32(0f, 0f), Point2F32(w0.toFloat(), 0f),
            listOf(
                GradientStop(0f, ColorARGB.fromPackedUInt(0xFF00FFFFu)),
                GradientStop(0.5f, ColorARGB.fromPackedUInt(0xFFFF00FFu)),
                GradientStop(1f, ColorARGB.fromPackedUInt(0xFFFFFF00u)),
            ),
        )
        val thresholdGrad = Shader.RadialGradient(
            Point2F32(w0 / 2f, h0 / 2f), w0 / 4f,
            listOf(
                GradientStop(0f, ColorARGB.Black),
                GradientStop(1f, ColorARGB.White),
            ),
        )

        val slopes = listOf(1f, 2f, 4f, 8f)
        var y = 0f
        for (slope in slopes) {
            val uniforms = UniformBlock {
                float1("cutoff", 0.5f)
                float1("slope", slope)
            }
            val shader = effect.makeShader(
                uniforms,
                mapOf(
                    "before_map" to beforeGrad,
                    "after_map" to afterGrad,
                    "threshold_map" to thresholdGrad,
                ),
            )
            val cellH = h0 / slopes.size
            canvas.drawRect(RectF32(0f, y, w0.toFloat(), y + cellH), Paint(shader = shader))
            y += cellH
        }
    }
}
