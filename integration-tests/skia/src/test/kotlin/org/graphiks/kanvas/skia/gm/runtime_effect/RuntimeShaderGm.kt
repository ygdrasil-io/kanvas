package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.SimpleRTWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp`.
 *
 * Builds a [SkRuntimeEffect] from the upstream SkSL :
 *
 * ```glsl
 * uniform half4 gColor;
 * half4 main(float2 p) {
 *     return half4(p*(1.0/255), gColor.b, 1);
 * }
 * ```
 *
 * Sets `gColor = (1, 0, 0, 1)` (so blue channel = 0 → output blue is
 * always zero, and red channel comes from `p.y` not the uniform), then
 * draws a 256 × 256 rect with the shader rotated 90° around `(128, 128)`
 * via a local matrix.
 *
 * The upstream SkSL is hand-ported into [SkBuiltinShaderEffectsSimple]'s
 * dispatch registry, so [SkRuntimeEffect.MakeForShader] resolves it
 * without an SkSL parser.
 *
 * Reference image: `runtime_shader.png`, 512 × 256.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class RuntimeShaderGm : SkiaGm {
    override val name = "runtime_shader"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(SimpleRTWgsl).getOrThrow()
        val uniforms = UniformBlock {
            float4("gColor", 1f, 0f, 0f, 1f)
        }
        val shader = effect.makeShader(uniforms)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }
}
