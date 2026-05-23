package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.SAVE_BEHIND: SkCanvasPriv::SaveBehind / DrawBehind not implemented in :kanvas-skia")
class SaveBehindTest {

    @Test
    fun `SaveBehindGM placeholder`() {
        TestUtils.runGmTest(SaveBehindGM())
    }
}
