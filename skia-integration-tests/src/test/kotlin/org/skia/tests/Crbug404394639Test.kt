package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.IMAGE_MAKE_SCALED: SkImage.makeScaled is not yet implemented in the kanvas-skia raster backend")
class Crbug404394639Test {

    @Test
    fun `Crbug404394639GM matches reference`() {
        val gm = Crbug404394639GM()
        TestUtils.runGmTest(gm)
    }
}
