package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsExponentialWgsl
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
 * 2-column × 5-row grid of exponential intrinsics. Each cell
 * resolves through the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsExponential]
 * cluster (Phase D2.4.c.2) — registered against the same
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * skeleton as the trig cluster, just with different math lambdas.
 *
 * Same drift sources as `RuntimeIntrinsicsTrigGM` (text labels,
 * sub-surface sRGB→Rec.2020 composite, polyline AA).
 * @see https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp
 */
class IntrinsicsExponentialGm : SkiaGm {
    override val name = "runtime_intrinsics_exponential"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(IntrinsicsExponentialWgsl).getOrThrow()
        val caseCount = 7
        for (testCase in 0 until caseCount) {
            val (x, y) = when (testCase) {
                0 -> 2.0f to 3.0f
                1 -> 1.0f to 0.0f
                2 -> 2.0f to 0.0f
                3 -> 2.0f to 0.0f
                4 -> 2.0f to 0.0f
                5 -> 4.0f to 0.0f
                6 -> 2.0f to 0.0f
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
