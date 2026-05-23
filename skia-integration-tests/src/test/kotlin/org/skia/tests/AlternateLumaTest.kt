package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COLOR_FILTER_PRIV: SkColorFilterPriv::WithWorkingFormat requires per-pixel colour-space xform steps in the raster pipeline — see API_FINALIZATION_PLAN.md")
class AlternateLumaTest {

    @Test
    fun `AlternateLumaGM matches reference`() {
        val gm = AlternateLumaGM()
        TestUtils.runGmTest(gm)
    }
}
