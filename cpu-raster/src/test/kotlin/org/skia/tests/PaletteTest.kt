package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COLR_V1: requires FreeType+HarfBuzz COLR v1 path via JNI — see API_FINALIZATION_PLAN.md")
class PaletteTest {

    @Test
    fun `PaletteGM matches reference`() {
        val gm = PaletteGM()
        TestUtils.runGmTest(gm)
    }
}
