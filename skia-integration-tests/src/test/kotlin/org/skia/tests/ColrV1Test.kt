package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COLR_V1: requires FreeType+HarfBuzz COLR v1 path via JNI — see API_FINALIZATION_PLAN.md")
class ColrV1Test {

    @Test
    fun `ColrV1GM matches reference`() {
        val gm = ColrV1GM()
        TestUtils.runGmTest(gm)
    }
}
