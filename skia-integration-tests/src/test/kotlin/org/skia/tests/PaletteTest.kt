package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COLR_V1: still targets the cpu-raster SkColrV1 surface stub; migrate or isolate in #1020")
class PaletteTest {

    @Test
    fun `PaletteGM matches reference`() {
        val gm = PaletteGM()
        TestUtils.runGmTest(gm)
    }
}
