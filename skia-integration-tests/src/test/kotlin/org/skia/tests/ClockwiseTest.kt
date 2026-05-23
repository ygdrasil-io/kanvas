package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GPU_CLOCKWISE: requires GrGeometryProcessor + GrDrawOp + SurfaceDrawContext Ganesh GPU path — sk_Clockwise SkSL built-in has no equivalent in the CPU/WebGPU pipeline")
class ClockwiseTest {

    @Test
    fun `ClockwiseGM matches reference`() {
        val gm = ClockwiseGM()
        TestUtils.runGmTest(gm)
    }
}
