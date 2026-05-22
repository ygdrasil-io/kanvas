package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled("STUB.COLOR_CUBE_LUT: needs 3D LUT color-cube filter + GPU shader sampling")
class ColorCubeTest {

    @Test
    fun `ColorCubeGM matches reference`() {
        val gm = ColorCubeGM()
        TestUtils.runGmTest(gm)
    }
}
