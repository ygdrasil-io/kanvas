package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.IMAGE_REINTERPRET_COLOR_SPACE: MakeColorSpaceGM calls " +
        "SkImage.reinterpretColorSpace() which is not yet implemented in " +
        "kanvas-skia. The conversion via makeColorSpace() works, but the " +
        "subsequent re-tag of the converted pixels under a standard sRGB glass " +
        "requires a GPU-level handle that the CPU raster backend cannot model. " +
        "Body fully ported; stub throws at runtime.",
)
class MakeColorSpaceTest {

    @Test
    fun `MakeColorSpaceGM matches makecolorspace reference`() {
        val gm = MakeColorSpaceGM()
        TestUtils.runGmTest(gm)
    }
}
