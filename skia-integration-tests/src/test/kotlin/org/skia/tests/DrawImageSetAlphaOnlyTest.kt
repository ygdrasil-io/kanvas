package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.EDGE_AA_IMAGE_SET: requires SkCanvas.experimental_DrawEdgeAAImageSet — " +
        "DrawImageSetAlphaOnlyGM probes the per-entry alpha multiplier + " +
        "alpha-only (`SkColorType.kAlpha_8`) colour-tagged image path through " +
        "the batched API. Body fully ported against the live surface ; the " +
        "matching device implementation resolves to " +
        "`TODO(\"STUB.EDGE_AA_IMAGE_SET\")` until the batched entry-point lands " +
        "in `kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt`.",
)
class DrawImageSetAlphaOnlyTest {

    @Test
    fun `DrawImageSetAlphaOnlyGM matches reference`() {
        val gm = DrawImageSetAlphaOnlyGM()
        TestUtils.runGmTest(gm)
    }
}
