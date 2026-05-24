package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GR_FRAGMENT_PROCESSOR: fp_sample_chaining requires Ganesh GrFragmentProcessor / GrPaint / SurfaceDrawContext — no CPU-raster equivalent in kanvas-skia")
class FpSampleChainingTest {

    @Test
    fun `FpSampleChainingGM matches reference`() {
        val gm = FpSampleChainingGM()
        TestUtils.runGmTest(gm)
    }
}
