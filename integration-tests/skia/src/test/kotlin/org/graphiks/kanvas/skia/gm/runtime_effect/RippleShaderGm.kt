package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.RippleWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/rippleshader.cpp`.
 *
 * Iso-aligned port of upstream's `gm/rippleshader.cpp` (which compiles
 * a custom SkSL runtime-effect shader that animates a wave-front pattern
 * across the canvas).
 *
 * Kanvas does not parse arbitrary SkSL — it dispatches through
 * a hand-port-per-shader-hash table. The custom RippleShader SkSL is
 * therefore not registered ; the runtime effect returns
 * an error and the GM cannot draw anything meaningful.
 *
 * The body calls the compile so the compile contract holds.
 * See [`API_FINALIZATION_PLAN.md`] § STUB.SKSL.
 * @see https://github.com/google/skia/blob/main/gm/rippleshader.cpp
 */
class RippleShaderGm : SkiaGm {
    override val name = "rippleshader"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.BLOCKING
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
