package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.EDGE_AA_IMAGE_SET: requires SkCanvas.experimental_DrawEdgeAAImageSet — " +
        "the rect-stays-rect family of CTMs exercises the batched per-entry " +
        "alpha multiplier + filter-mode behaviour. Body fully ported against " +
        "the live API surface ; the matching device implementation resolves to " +
        "`TODO(\"STUB.EDGE_AA_IMAGE_SET\")` until the batched entry-point lands " +
        "in `kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt`.",
)
class DrawImageSetRectToRectTest {

    @Test
    fun `DrawImageSetRectToRectGM matches reference`() {
        val gm = DrawImageSetRectToRectGM()
        TestUtils.runGmTest(gm)
    }
}
