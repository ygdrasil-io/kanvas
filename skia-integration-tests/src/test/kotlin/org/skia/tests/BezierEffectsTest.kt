package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Tests for [BezierConicEffectsGM] and [BezierQuadEffectsGM].
 *
 * Both GMs are **INTRACTABLE.GANESH_EFFECT**: they are `GpuGM`s that drive
 * internal Ganesh geometry-processor primitives (`GrConicEffect` /
 * `GrQuadEffect`) through `GrMeshDrawOp` and `SurfaceDrawContext::addDrawOp`.
 * The draw pipeline bypasses the public `SkCanvas` API entirely and requires
 * a live `GrRecordingContext` which does not exist in the CPU-raster /
 * WebGPU backends of `:kanvas-skia`. The GM bodies throw
 * `TODO("STUB.GANESH_BEZIER_EFFECT")`.
 *
 * Source: `gm/beziereffects.cpp` (`BezierConicEffects`, `BezierQuadEffects`).
 */
class BezierEffectsTest {

    @Disabled(
        "INTRACTABLE.GANESH_EFFECT: bezier_conic_effects is a GpuGM that renders " +
            "conic-curve fill triangles through GrConicEffect (a Ganesh internal " +
            "geometry processor in src/gpu/ganesh/effects/GrBezierEffect.h) via " +
            "BezierConicTestOp -> SurfaceDrawContext::addDrawOp. No GrRecordingContext " +
            "is available in the CPU/WebGPU pipeline; body throws " +
            "TODO(\"STUB.GANESH_BEZIER_EFFECT\"). Upstream: gm/beziereffects.cpp.",
    )
    @Test
    fun `BezierConicEffectsGM is Ganesh-only INTRACTABLE`() {
        val gm = BezierConicEffectsGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GANESH_EFFECT: bezier_quad_effects is a GpuGM that renders " +
            "quadratic-curve fill triangles through GrQuadEffect (a Ganesh internal " +
            "geometry processor in src/gpu/ganesh/effects/GrBezierEffect.h) via " +
            "BezierQuadTestOp -> SurfaceDrawContext::addDrawOp. No GrRecordingContext " +
            "is available in the CPU/WebGPU pipeline; body throws " +
            "TODO(\"STUB.GANESH_BEZIER_EFFECT\"). Upstream: gm/beziereffects.cpp.",
    )
    @Test
    fun `BezierQuadEffectsGM is Ganesh-only INTRACTABLE`() {
        val gm = BezierQuadEffectsGM()
        TestUtils.runGmTest(gm)
    }
}
