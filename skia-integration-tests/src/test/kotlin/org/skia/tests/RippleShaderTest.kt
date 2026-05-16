package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.SKSL: requires Skia SkSL parser (~30k LOC) — out of scope for kanvas-skia pure-JVM, see API_FINALIZATION_PLAN.md")
class RippleShaderTest {

    @Test
    fun `RippleShaderGM matches reference`() {
        val gm = RippleShaderGM()
        TestUtils.runGmTest(gm)
    }
}
