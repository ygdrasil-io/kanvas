package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsRelationalWgsl
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.RuntimeEffect
import org.graphiks.kanvas.pipeline.UniformBlock
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeintrinsics.cpp`.
 *
 * 4-column × 6-row grid (some rows have only 2 cells). Each cell
 * exercises one bvec relational expression — `lessThan` /
 * `lessThanEqual` / `greaterThan` / `greaterThanEqual` / `equal` /
 * `notEqual`, in float and int variants, plus bvec compositions
 * (`equal(le, ge)`), `not()` inverses, and `any` / `all` reductions
 * broadcast back to bool2.
 *
 * Resolves through
 * [SkBuiltinShaderEffectsIntrinsicsRelational]
 * cluster (Phase D2.4.c.6).
 * @see https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp
 */
class IntrinsicsRelationalGm : SkiaGm {
    override val name = "runtime_intrinsics_relational"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(IntrinsicsRelationalWgsl).getOrThrow()
        val caseCount = 8
        for (testCase in 0 until caseCount) {
            val (a, b) = when (testCase) {
                0 -> floatArrayOf(0.0f, 0.5f, 0.0f, 0.0f) to floatArrayOf(0.5f, 0.5f, 0.0f, 0.0f)
                1 -> floatArrayOf(0.5f, 0.0f, 0.0f, 0.0f) to floatArrayOf(0.0f, 0.5f, 0.0f, 0.0f)
                2 -> floatArrayOf(0.5f, 0.5f, 0.0f, 0.0f) to floatArrayOf(0.5f, 0.5f, 0.0f, 0.0f)
                3 -> floatArrayOf(0.5f, 0.0f, 0.0f, 0.0f) to floatArrayOf(0.0f, 0.5f, 0.0f, 0.0f)
                4 -> floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f) to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
                5 -> floatArrayOf(0.5f, 0.5f, 0.0f, 0.0f) to floatArrayOf(0.5f, 0.5f, 0.0f, 0.0f)
                6 -> floatArrayOf(Float.NaN, 0.0f, 0.0f, 0.0f) to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
                7 -> floatArrayOf(Float.POSITIVE_INFINITY, 0.0f, 0.0f, 0.0f) to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
                else -> floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f) to floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
            }
            val uniforms = UniformBlock {
                int1("testCase", testCase)
                float4("a", a[0], a[1], a[2], a[3])
                float4("b", b[0], b[1], b[2], b[3])
            }
            val shader = effect.makeShader(uniforms)
            val cx = (testCase % 4) * 128f
            val cy = (testCase / 4) * 128f
            canvas.drawRect(Rect(cx, cy, cx + 128f, cy + 128f), Paint(shader = shader))
        }
    }
}
