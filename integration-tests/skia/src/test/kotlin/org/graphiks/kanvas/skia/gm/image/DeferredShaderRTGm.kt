package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp` deferred_shader_rt.
 *
 * Draws three 50×50 rectangles filled with red, green, and blue via
 * a deferred runtime shader that returns a uniform color. Each draw
 * uses a different uniform block, mirroring Skia's
 * [SkRuntimeEffectPriv::MakeDeferredShader] pattern.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class DeferredShaderRTGm : SkiaGm {
    override val name = "deferred_shader_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 150
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(SimpleRTWgsl).getOrThrow()
        val colors = listOf(
            floatArrayOf(1f, 0f, 0f, 1f),
            floatArrayOf(0f, 1f, 0f, 1f),
            floatArrayOf(0f, 0f, 1f, 1f),
        )
        for (c in colors) {
            val uniforms = UniformBlock {
                float4("gColor", c[0], c[1], c[2], c[3])
            }
            val shader = effect.makeShader(uniforms)
            canvas.drawRect(Rect(0f, 0f, 50f, 50f), Paint(shader = shader))
            canvas.translate(50f, 0f)
        }
    }
}
