package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GPU_RRECT_EFFECT: requires GrRRectEffect + SurfaceDrawContext Ganesh GPU path — not available in CPU/WebGPU pipeline")
class RRectEffectTest {

    @Test
    fun `RRectEffectGM matches reference`() {
        val gm = RRectEffectGM()
        TestUtils.runGmTest(gm)
    }
}
