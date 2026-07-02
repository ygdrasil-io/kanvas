package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsTrigWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class IntrinsicsTrigGm : SkiaGm {
    override val name = "runtime_intrinsics_trig"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 384

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(IntrinsicsTrigWgsl).getOrThrow()
        val caseCount = 9
        for (testCase in 0 until caseCount) {
            val (x, y) = when (testCase) {
                0 -> 0.5f to 0.0f
                1 -> 0.5f to 0.0f
                2 -> 0.5f to 0.0f
                3 -> 0.5f to 0.0f
                4 -> 0.5f to 0.0f
                5 -> 0.5f to 0.5f
                6 -> 0.5f to 0.0f
                7 -> 0.5f to 0.0f
                8 -> 0.5f to 0.0f
                else -> 0.0f to 0.0f
            }
            val uniforms = UniformBlock {
                int1("testCase", testCase)
                float1("x", x)
                float1("y", y)
            }
            val shader = effect.makeShader(uniforms)
            val cx = (testCase % 4) * 128f
            val cy = (testCase / 4) * 128f
            canvas.drawRect(Rect(cx, cy, cx + 128f, cy + 128f), Paint(shader = shader))
        }
    }
}
