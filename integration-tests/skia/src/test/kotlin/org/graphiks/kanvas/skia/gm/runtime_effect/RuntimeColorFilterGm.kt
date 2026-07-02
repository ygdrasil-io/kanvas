package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterNoopWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterLumaToAlphaWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterTernaryWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterIfsWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.ColorFilterEarlyReturnWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class RuntimeColorFilterGm : SkiaGm {
    override val name = "runtimecolorfilter"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 768
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val cell = 256f

        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.WHITE))

        drawCell(canvas, 0, 0, ColorFilterNoopWgsl, UniformBlock {})
        drawCell(canvas, 1, 0, ColorFilterLumaToAlphaWgsl, UniformBlock {
            float4("srcColor", 0.5f, 0.2f, 0.8f, 1.0f)
        })
        drawCell(canvas, 0, 1, ColorFilterTernaryWgsl, UniformBlock {
            float1("condition", 1.0f)
            float4("colorTrue", 0.2f, 0.6f, 1.0f, 1.0f)
            float4("colorFalse", 1.0f, 0.2f, 0.2f, 1.0f)
        })
        drawCell(canvas, 1, 1, ColorFilterIfsWgsl, UniformBlock {
            float1("value", 0.5f)
        })
        drawCell(canvas, 2, 1, ColorFilterEarlyReturnWgsl, UniformBlock {
            float1("threshold", 0.5f)
            float4("input", 0.0f, 0.8f, 0.0f, 1.0f)
        })
    }

    private fun drawCell(canvas: GmCanvas, col: Int, row: Int, wgsl: String, uniforms: UniformBlock) {
        val effect = RuntimeEffect.compile(wgsl).getOrThrow()
        val shader = effect.makeShader(uniforms)
        val x = col * 256f
        val y = row * 256f
        canvas.drawRect(Rect(x, y, x + 256f, y + 256f), Paint(shader = shader))
    }
}
