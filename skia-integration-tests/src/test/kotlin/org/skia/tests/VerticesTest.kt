package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.DRAW_VERTICES: full VerticesGM blend/shader grid still pending; VerticesBatchingGM is enabled")
class VerticesTest {

    @Test
    fun `VerticesGM placeholder`() {
        TestUtils.runGmTest(VerticesGM())
    }
}
