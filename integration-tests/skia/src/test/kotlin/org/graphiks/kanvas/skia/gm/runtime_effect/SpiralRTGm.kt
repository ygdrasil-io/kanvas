package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.SpiralRTWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import kotlin.math.sin

class SpiralRTGm : SkiaGm {
    override val name = "spiral_rt"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(SpiralRTWgsl).getOrThrow()
        val uniforms = UniformBlock {
            float4("center", 256f, 256f, 0f, 0f)
            float4("color1", 1f, 0f, 0f, 1f)
            float4("color2", 0f, 1f, 0f, 1f)
            float4("params", sin(2.0f) / 5f, 0f, 0f, 0f)
        }
        val shader = effect.makeShader(uniforms)
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }
}
