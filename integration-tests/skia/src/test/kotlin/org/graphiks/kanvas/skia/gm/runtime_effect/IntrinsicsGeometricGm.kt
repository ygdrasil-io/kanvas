package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.gpu.renderer.wgsl.IntrinsicsGeometricWgsl
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
 * 4-column × 5-row grid covering GLSL geometric functions :
 * `length` / `distance` / `dot` / `cross` / `normalize` /
 * `faceforward` / `reflect` / `refract`.
 *
 * Resolves through the
 * [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsGeometric]
 * cluster (Phase D2.4.c.4).
 * @see https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp
 */
class IntrinsicsGeometricGm : SkiaGm {
    override val name = "runtime_intrinsics_geometric"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 425
    override val height = 605

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val effect = RuntimeEffect.compile(IntrinsicsGeometricWgsl).getOrThrow()
        val caseCount = 7
        data class Vec3(val x: Float, val y: Float, val z: Float)
        data class TwoVec3(val a: Vec3, val b: Vec3)
        for (testCase in 0 until caseCount) {
            val (a, b) = when (testCase) {
                0 -> TwoVec3(Vec3(0.5f, -0.5f, 0.0f), Vec3(0.0f, 0.0f, 0.0f))
                1 -> TwoVec3(Vec3(1.0f, 0.0f, 0.0f), Vec3(0.0f, 0.0f, 0.0f))
                2 -> TwoVec3(Vec3(0.5f, 0.5f, 0.0f), Vec3(0.5f, -0.5f, 0.0f))
                3 -> TwoVec3(Vec3(0.5f, 0.0f, 0.0f), Vec3(0.0f, 0.5f, 0.0f))
                4 -> TwoVec3(Vec3(1.0f, 0.0f, 0.0f), Vec3(0.0f, 0.0f, 0.0f))
                5 -> TwoVec3(Vec3(1.0f, -1.0f, 0.0f), Vec3(0.0f, 1.0f, 0.0f))
                6 -> TwoVec3(Vec3(1.0f, 0.0f, 0.0f), Vec3(0.0f, 1.0f, 0.0f))
                else -> TwoVec3(Vec3(0.0f, 0.0f, 0.0f), Vec3(0.0f, 0.0f, 0.0f))
            }
            val uniforms = UniformBlock {
                int1("testCase", testCase)
                float3("a", a.x, a.y, a.z)
                float3("b", b.x, b.y, b.z)
            }
            val shader = effect.makeShader(uniforms)
            val cx = (testCase % 4) * 128f
            val cy = (testCase / 4) * 128f
            canvas.drawRect(Rect(cx, cy, cx + 128f, cy + 128f), Paint(shader = shader))
        }
    }
}
