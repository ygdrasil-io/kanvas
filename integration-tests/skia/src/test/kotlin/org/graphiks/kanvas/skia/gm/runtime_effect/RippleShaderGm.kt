package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.RippleWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class RippleShaderGm : SkiaGm {
    override val name = "rippleshader"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(RippleWgsl).getOrThrow()
        val uniforms = UniformBlock {
            float1("time", 0.0f)
            float1("amplitude", 0.5f)
            float1("frequency", 10.0f)
            float2("center", 256f, 256f)
        }
        val shader = effect.makeShader(uniforms)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }
}
