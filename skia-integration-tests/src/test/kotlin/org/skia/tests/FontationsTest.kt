package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.FONTATIONS: requires Fontations Rust crate via UniFFI/JNI — see API_FINALIZATION_PLAN.md")
class FontationsTest {

    @Test
    fun `FontationsGM matches reference`() {
        val gm = FontationsGM()
        TestUtils.runGmTest(gm)
    }
}
