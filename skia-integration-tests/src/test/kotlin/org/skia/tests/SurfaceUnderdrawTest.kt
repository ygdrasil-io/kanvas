package org.skia.tests

import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class SurfaceUnderdrawTest {

    @Test
    fun `SurfaceUnderdrawGM renders subset snapshot path`() {
        TestUtils.runGmTest(SurfaceUnderdrawGM())
    }
}
