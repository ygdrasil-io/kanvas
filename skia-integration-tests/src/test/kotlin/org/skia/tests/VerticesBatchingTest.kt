package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.DRAW_VERTICES: SkCanvas.drawVertices not implemented in :kanvas-skia — " +
        "the vertex-mesh rasterisation pipeline is GPU-plan only and is not yet " +
        "wired through the CPU-raster canvas dispatcher. " +
        "Drop this @Disabled once drawVertices lands.",
)
class VerticesBatchingTest {

    @Test
    fun `VerticesBatchingGM placeholder`() {
        TestUtils.runGmTest(VerticesBatchingGM())
    }
}
