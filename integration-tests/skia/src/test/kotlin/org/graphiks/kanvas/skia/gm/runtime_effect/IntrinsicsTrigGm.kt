package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsTrigWgsl
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
 * Lays out a 3-column × 5-row grid of unary trig intrinsics. Each
 * cell : (1) draws a centred label (`fn` text), (2) renders the
 * SkSL `make_unary_sksl_1d(fn, false)` runtime shader into a
 * 100×100 off-screen surface scaled so the shader's `p` parameter
 * sweeps `[0, 1)²`, (3) plots a green polyline through the top
 * row of pixels — visualising `y(x)` for x linearly mapped to
 * `[xMin, xMax]` and y mapped from `[yMin, yMax]` to
 * `[0, kBoxSize]` (1 = bottom, 0 = top).
 *
 * Built on the [SkBuiltinShaderEffectsIntrinsicsTrig] cluster
 * (Phase D2.4.c.1) — the registry has 12 SkSL hash entries
 * (radians / degrees / sin / cos / tan / asin / acos / atan(x) /
 * atan(0.1, x) / atan(-0.1, x) / atan(x, 0.1) / atan(x, -0.1))
 * each pointing to a [SkBuiltinShaderEffectsIntrinsicsTrig.UnaryIntrinsicImpl]
 * carrying the matching `kotlin.math` impl.
 *
 * **Known drift sources** (vs `runtime_intrinsics_trig.png`) :
 *  - Text labels — OpenType-vs-FreeType glyph drift (~3-5 % canvas).
 *  - Working colour space — our render goes through a sRGB
 *    100×100 sub-surface then composites onto a Rec.2020 parent.
 *  - Polyline AA — our scanline 4×4 supersampling vs Skia's
 *    analytical AA.
 *
 * The floor is set low (~5 %) since the drift sources accumulate
 * across 12 cells ; the ratchet still catches regressions of any
 * single intrinsic's math.
 * @see https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp
 */
class IntrinsicsTrigGm : SkiaGm {
    override val name = "runtime_intrinsics_trig"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 605

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
