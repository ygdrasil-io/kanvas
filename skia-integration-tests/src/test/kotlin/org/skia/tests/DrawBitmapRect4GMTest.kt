package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawBitmapRect4GMTest {

    @Disabled("bigbitmaprect_s/i — 4096×4096 XOR-blend tiling test; raster path is slow, GPU seeding required")
    @Test
    fun `DrawBitmapRect4 float-src variant matches bigbitmaprect_s_png within tolerance`() {
        runVariant(DrawBitmapRect4GM(DrawBitmapRect4GM.Variant.FLOAT), "DrawBitmapRect4GM_float")
    }

    @Disabled("bigbitmaprect_s/i — 4096×4096 XOR-blend tiling test; raster path is slow, GPU seeding required")
    @Test
    fun `DrawBitmapRect4 int-src variant matches bigbitmaprect_i_png within tolerance`() {
        runVariant(DrawBitmapRect4GM(DrawBitmapRect4GM.Variant.INT), "DrawBitmapRect4GM_int")
    }

    private fun runVariant(gm: DrawBitmapRect4GM, label: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed(label, comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(label, comparison.similarity)
        assertTrue(accepted, "$label regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "$label similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=2 floor)",
        )
    }
}
