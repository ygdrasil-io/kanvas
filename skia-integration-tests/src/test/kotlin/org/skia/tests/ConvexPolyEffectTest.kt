package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GPU_CONVEX_POLY_EFFECT: requires GrConvexPolyEffect + GrClipEdgeType + SurfaceDrawContext Ganesh GPU path — not available in CPU/WebGPU pipeline")
class ConvexPolyEffectTest {

    @Test
    fun `ConvexPolyEffectGM matches reference`() {
        val gm = ConvexPolyEffectGM()
        TestUtils.runGmTest(gm)
    }
}
