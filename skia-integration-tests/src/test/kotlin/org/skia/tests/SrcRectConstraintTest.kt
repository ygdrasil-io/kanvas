package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.SRC_RECT_CONSTRAINT: SkCanvas.drawImageRect lacks SrcRectConstraint param")
class SrcRectConstraintTest {

    @Test
    fun `SrcRectConstraintGM placeholder`() {
        TestUtils.runGmTest(SrcRectConstraintGM())
    }
}
