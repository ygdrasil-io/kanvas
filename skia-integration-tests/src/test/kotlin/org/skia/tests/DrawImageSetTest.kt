package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.EDGE_AA_IMAGE_SET: requires SkCanvas.experimental_DrawEdgeAAImageSet — " +
        "batched per-image draw with per-entry edge-AA flags / alpha / dst-clip " +
        "currently resolves to `TODO(\"STUB.EDGE_AA_IMAGE_SET\")` in " +
        "`kanvas-skia/src/main/kotlin/org/skia/core/SkCanvas.kt`. Body of " +
        "DrawImageSetGM is fully ported against the live API surface (4 CTMs × " +
        "2 filter modes × tile grid + per-edge AA flag plumbing) — drop this " +
        "`@Disabled` once the batched device entry-point lands.",
)
class DrawImageSetTest {

    @Test
    fun `DrawImageSetGM matches reference`() {
        val gm = DrawImageSetGM()
        TestUtils.runGmTest(gm)
    }
}
