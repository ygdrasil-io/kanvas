package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsCommonWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeintrinsics.cpp`.
 *
 * 6-column × 7-row grid covering the GLSL "common" function
 * family : abs / sign / floor / ceil / fract / mod / min / max /
 * clamp / saturate / mix / step / smoothstep + the row-7
 * componentwise `floor(p)` / `ceil(p)` plots.
 *
 * Resolves through the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsCommon]
 * cluster (Phase D2.4.c.3) which registers 31 SkSL hashes against
 * the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * skeleton.
 * @see https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp
 */
class IntrinsicsCommonGm : SkiaGm {
    override val name = "runtime_intrinsics_common"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(IntrinsicsCommonWgsl).getOrThrow()
        val caseCount = 13
        for (testCase in 0 until caseCount) {
            val (a, b, edge) = when (testCase) {
                0 -> floatArrayOf(0.0f, 1.0f, 0.0f)
                1 -> floatArrayOf(0.5f, 0.0f, 0.0f)
                2 -> floatArrayOf(0.5f, 0.0f, 0.0f)
                3 -> floatArrayOf(0.5f, 0.0f, 0.25f)
                4 -> floatArrayOf(0.5f, 0.0f, 0.0f)
                5 -> floatArrayOf(-0.5f, 0.0f, 0.0f)
                6 -> floatArrayOf(0.5f, 0.0f, 0.0f)
                7 -> floatArrayOf(0.7f, 0.0f, 0.0f)
                8 -> floatArrayOf(0.3f, 0.0f, 0.0f)
                9 -> floatArrayOf(1.7f, 0.0f, 0.0f)
                10 -> floatArrayOf(1.3f, 1.0f, 0.0f)
                11 -> floatArrayOf(0.3f, 0.8f, 0.0f)
                12 -> floatArrayOf(0.3f, 0.8f, 0.0f)
                else -> floatArrayOf(0.0f, 0.0f, 0.0f)
            }
            val uniforms = UniformBlock {
                int1("testCase", testCase)
                float1("a", a)
                float1("b", b)
                float1("edge", edge)
            }
            val shader = effect.makeShader(uniforms)
            val x = (testCase % 4) * 128f
            val y = (testCase / 4) * 128f
            canvas.drawRect(Rect(x, y, x + 128f, y + 128f), Paint(shader = shader))
        }
    }
}
