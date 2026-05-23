package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.DRAW_VERTICES: SkCanvas.drawVertices not implemented")
class VerticesTest {

    @Test
    fun `VerticesGM placeholder`() {
        TestUtils.runGmTest(VerticesGM())
    }
}
