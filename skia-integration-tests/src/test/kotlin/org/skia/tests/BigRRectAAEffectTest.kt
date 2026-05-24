package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.GPU_BIG_RRECT_AA_EFFECT: requires GrRRectEffect + SurfaceDrawContext + " +
        "GrPaint + GrPorterDuffXPFactory + FillRectOp Ganesh GPU path — " +
        "not available in CPU/WebGPU pipeline"
)
class BigRRectAAEffectTest {

    @Test
    fun `BigRRectRectAAEffectGM matches reference`() {
        val gm = BigRRectRectAAEffectGM()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `BigRRectCircleAAEffectGM matches reference`() {
        val gm = BigRRectCircleAAEffectGM()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `BigRRectEllipseAAEffectGM matches reference`() {
        val gm = BigRRectEllipseAAEffectGM()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `BigRRectCircularCornerAAEffectGM matches reference`() {
        val gm = BigRRectCircularCornerAAEffectGM()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `BigRRectEllipticalCornerAAEffectGM matches reference`() {
        val gm = BigRRectEllipticalCornerAAEffectGM()
        TestUtils.runGmTest(gm)
    }
}
