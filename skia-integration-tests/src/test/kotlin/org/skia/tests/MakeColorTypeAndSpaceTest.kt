package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.IMAGE_MAKE_COLOR_TYPE_AND_SPACE: MakeColorTypeAndSpaceGM calls " +
        "SkImage.makeColorTypeAndColorSpace() which is not yet implemented in " +
        "kanvas-skia. Upstream uses a single GPU blit to re-encode both the " +
        "colour type (e.g. kRGB_565 or kGray_8) and the colour space " +
        "(e.g. Rec.2020 wide-gamut) in one pass. The CPU raster equivalent " +
        "would require a pixmap conversion + colour-space xform pipeline. " +
        "Body fully ported; stub throws at runtime.",
)
class MakeColorTypeAndSpaceTest {

    @Test
    fun `MakeColorTypeAndSpaceGM matches makecolortypeandspace reference`() {
        val gm = MakeColorTypeAndSpaceGM()
        TestUtils.runGmTest(gm)
    }
}
