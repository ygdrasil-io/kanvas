package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.FONTATIONS: requires Fontations Rust crate via UniFFI/JNI — see API_FINALIZATION_PLAN.md")
class FontationsFtCompareTest {

    @Test
    fun `FontationsFtCompareGM matches reference`() {
        val gm = FontationsFtCompareGM()
        TestUtils.runGmTest(gm)
    }
}
