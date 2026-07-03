package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.ThresholdRTWgsl
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
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(ThresholdRTWgsl).getOrThrow()
        val w0 = width
        val h0 = height

        val beforeGrad = Shader.LinearGradient(
            Point(0f, 0f), Point(w0.toFloat(), h0.toFloat()),
            listOf(
                GradientStop(0f, Color.RED),
                GradientStop(0.5f, Color.GREEN),
                GradientStop(1f, Color.BLUE),
            ),
        )
        val afterGrad = Shader.LinearGradient(
            Point(0f, 0f), Point(w0.toFloat(), 0f),
            listOf(
                GradientStop(0f, Color(0xFF00FFFFu)),
                GradientStop(0.5f, Color(0xFFFF00FFu)),
                GradientStop(1f, Color(0xFFFFFF00u)),
            ),
        )
        val thresholdGrad = Shader.RadialGradient(
            Point(w0 / 2f, h0 / 2f), w0 / 4f,
            listOf(
                GradientStop(0f, Color.BLACK),
                GradientStop(1f, Color.WHITE),
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
            canvas.drawRect(Rect(0f, y, w0.toFloat(), y + cellH), Paint(shader = shader))
            y += cellH
        }
    }
}
