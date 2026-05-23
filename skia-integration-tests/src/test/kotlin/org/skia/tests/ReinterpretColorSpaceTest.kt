package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.IMAGE_REINTERPRET_COLOR_SPACE: ReinterpretColorSpaceGM calls " +
        "SkImage.reinterpretColorSpace() which is not yet implemented in " +
        "kanvas-skia. Upstream re-tags an image's colour-space handle without " +
        "converting any pixel data — a GPU-level lazy re-wrap that has no direct " +
        "equivalent on the CPU raster path. makeColorSpace() (pixel conversion) " +
        "works; the re-tagging half throws. Body fully ported; stub throws at runtime.",
)
class ReinterpretColorSpaceTest {

    @Test
    fun `ReinterpretColorSpaceGM matches reinterpretcolorspace reference`() {
        val gm = ReinterpretColorSpaceGM()
        TestUtils.runGmTest(gm)
    }
}
