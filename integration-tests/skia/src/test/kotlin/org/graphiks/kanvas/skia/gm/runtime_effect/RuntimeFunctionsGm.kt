package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.RuntimeFunctionsWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class RuntimeFunctionsGm : SkiaGm {
    override val name = "runtimefunctions"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(RuntimeFunctionsWgsl).getOrThrow()
        val shader = effect.makeShader(UniformBlock { })
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
    }
}
