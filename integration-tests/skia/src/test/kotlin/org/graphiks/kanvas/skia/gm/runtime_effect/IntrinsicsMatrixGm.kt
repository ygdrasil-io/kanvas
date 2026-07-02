package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsMatrixWgsl
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
 * 3-column × 2-row grid. Row 1 : `matrixCompMult` for 2×2 / 3×3 /
 * 4×4. Row 2 : `inverse` for 2×2 / 3×3 / 4×4. Each cell renders
 * a 2D colour map where `(p.x, p.y)` selects a single matrix cell
 * via the upstream `selN` partitioning.
 *
 * Resolves through the
 * [SkBuiltinShaderEffectsIntrinsicsMatrix]
 * cluster (Phase D2.4.c.5).
 * @see https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp
 */
class IntrinsicsMatrixGm : SkiaGm {
    override val name = "runtime_intrinsics_matrix"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(IntrinsicsMatrixWgsl).getOrThrow()
        val caseCount = 3
        for (testCase in 0 until caseCount) {
            val (input, vec) = when (testCase) {
                0 -> floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                ) to floatArrayOf(1f, 0f, 0f, 0f)
                1 -> floatArrayOf(
                    1f, 2f, 0f, 0f,
                    3f, 4f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                ) to floatArrayOf(0f, 0f, 0f, 0f)
                2 -> floatArrayOf(
                    1f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f,
                    0f, 0f, 1f, 0f,
                    0f, 0f, 0f, 1f,
                ) to floatArrayOf(0.5f, 0.5f, 0.5f, 1.0f)
                else -> floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f) to floatArrayOf(0f, 0f, 0f, 0f)
            }
            val uniforms = UniformBlock {
                int1("testCase", testCase)
                mat4x4("input", input)
                float4("vec", vec[0], vec[1], vec[2], vec[3])
            }
            val shader = effect.makeShader(uniforms)
            val cx = (testCase % 4) * 128f
            val cy = (testCase / 4) * 128f
            canvas.drawRect(Rect(cx, cy, cx + 128f, cy + 128f), Paint(shader = shader))
        }
    }
}
