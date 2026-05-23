package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.PIXMAP_SCALE: requires SkPixmap.scalePixels")
class ScalePixelsTest {

    @Test
    fun `ScalePixelsGM placeholder`() {
        TestUtils.runGmTest(ScalePixelsGM())
    }
}
