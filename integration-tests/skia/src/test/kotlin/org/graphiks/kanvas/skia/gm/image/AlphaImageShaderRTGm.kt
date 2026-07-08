package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp` alpha_image_shader_rt (350 × 50).
 * Verifies that SkSL shaders/color-filters/blenders do not receive the
 * paint color applied to alpha-only image shaders.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class AlphaImageShaderRTGm : SkiaGm {
    override val name = "alpha_image_shader_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 350
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(SimpleRTWgsl).getOrThrow()
        val uniforms = UniformBlock { float4("gColor", 0.5f, 0f, 0.5f, 1f) }
        val shader = effect.makeShader(uniforms)

        val paint = Paint(color = Color.fromRGBA(0.5f, 0f, 0.5f, 1f))
        canvas.drawRect(Rect(0f, 0f, 48f, 48f), paint)
        canvas.translate(50f, 0f)

        canvas.drawRect(Rect(0f, 0f, 48f, 48f), paint.copy(shader = shader))
        canvas.translate(50f, 0f)

        canvas.drawRect(Rect(0f, 0f, 48f, 48f), paint.copy(shader = shader))
        canvas.translate(50f, 0f)

        canvas.drawRect(Rect(0f, 0f, 48f, 48f), paint.copy(shader = shader))
        canvas.translate(50f, 0f)

        canvas.drawRect(Rect(0f, 0f, 48f, 48f), paint.copy(shader = shader))
        canvas.translate(50f, 0f)

        canvas.drawRect(Rect(0f, 0f, 48f, 48f), paint.copy(shader = shader))
        canvas.translate(50f, 0f)

        canvas.drawRect(Rect(0f, 0f, 48f, 48f), paint.copy(shader = shader))
    }
}
