package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.GANESH: swizzle requires GrFragmentProcessor::SwizzleOutput + SurfaceFillContext — Ganesh GPU pipeline not available in :kanvas-skia JVM raster backend")
class SwizzleTest {

    @Test
    fun `SwizzleGM matches reference`() {
        val gm = SwizzleGM()
        TestUtils.runGmTest(gm)
    }
}
