package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp` — `null_child_rt`.
 *
 * Tests runtime effects that evaluate null child pointers
 * (shader, colorFilter, blender). Every swatch evaluates to
 * the same shade of purple `(0.5, 0, 0.5, 1)`.
 *
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class NullChildRTGm : SkiaGm {
    override val name = "null_child_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 150
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(NullChildRTWgsl).getOrThrow()
        val shader = effect.makeShader(UniformBlock {})

        canvas.save()
        for (row in 0 until 2) {
            for (col in 0 until 3) {
                val x = col * 50f
                val y = row * 50f
                canvas.drawRect(Rect(x, y, x + 48f, y + 48f), Paint(shader = shader))
            }
        }
        canvas.restore()
    }

    companion object {
        private const val NullChildRTWgsl: String = """
fn null_child_rt_source(uv: vec2<f32>) -> vec4<f32> {
    return vec4<f32>(0.5, 0.0, 0.5, 1.0);
}
"""
    }
}
