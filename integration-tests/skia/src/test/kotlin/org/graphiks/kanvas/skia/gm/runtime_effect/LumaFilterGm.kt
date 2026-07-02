package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.LumaFilterWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Color

class LumaFilterGm : SkiaGm {
    override val name = "lumafilter"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(LumaFilterWgsl).getOrThrow()

        val colors = listOf(
            floatArrayOf(1f, 0f, 0f, 1f),
            floatArrayOf(0f, 1f, 0f, 1f),
            floatArrayOf(0f, 0f, 1f, 1f),
            floatArrayOf(1f, 1f, 0f, 1f),
            floatArrayOf(1f, 0f, 1f, 1f),
            floatArrayOf(0f, 1f, 1f, 1f),
            floatArrayOf(0.5f, 0.5f, 0.5f, 1f),
            floatArrayOf(1f, 1f, 1f, 1f),
        )

        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.WHITE))

        for (i in colors.indices) {
            val c = colors[i]
            val uniforms = UniformBlock {
                float4("input", c[0], c[1], c[2], c[3])
            }
            val shader = effect.makeShader(uniforms)
            val x = (i % 4) * 128f
            val y = (i / 4) * 128f
            canvas.drawRect(Rect(x, y, x + 128f, y + 128f), Paint(shader = shader))
        }
    }
}
