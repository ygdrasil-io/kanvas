package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.ArithmodeWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class ArithmodeGm : SkiaGm {
    override val name = "arithmode"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(ArithmodeWgsl).getOrThrow()
        val caseCount = 3
        for (mode in 0 until caseCount) {
            val (src, dst) = when (mode) {
                0 -> floatArrayOf(0.5f, 0.2f, 0.8f, 1.0f) to floatArrayOf(0.3f, 0.6f, 0.1f, 0.8f)
                1 -> floatArrayOf(0.5f, 0.2f, 0.8f, 1.0f) to floatArrayOf(0.3f, 0.6f, 0.1f, 0.8f)
                2 -> floatArrayOf(0.5f, 0.2f, 0.8f, 1.0f) to floatArrayOf(0.3f, 0.6f, 0.1f, 0.8f)
                else -> floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f) to floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
            }
            val uniforms = UniformBlock {
                int1("mode", mode)
                float4("src", src[0], src[1], src[2], src[3])
                float4("dst", dst[0], dst[1], dst[2], dst[3])
            }
            val shader = effect.makeShader(uniforms)
            val cx = (mode % 4) * 128f
            val cy = (mode / 4) * 128f
            canvas.drawRect(Rect(cx, cy, cx + 128f, cy + 128f), Paint(shader = shader))
        }
    }
}
