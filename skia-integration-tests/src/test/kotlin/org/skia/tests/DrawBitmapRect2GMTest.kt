package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawBitmapRect2GMTest {

    @Disabled("bitmaprect_s/i — linear gradient circle; raster matches within tolerance once GPU seeded")
    @Test
    fun `DrawBitmapRect2 float-src variant matches bitmaprect_s_png within tolerance`() {
        runVariant(DrawBitmapRect2GM(DrawBitmapRect2GM.Variant.FLOAT), "DrawBitmapRect2GM_float")
    }

    @Disabled("bitmaprect_s/i — linear gradient circle; raster matches within tolerance once GPU seeded")
    @Test
    fun `DrawBitmapRect2 int-src variant matches bitmaprect_i_png within tolerance`() {
        runVariant(DrawBitmapRect2GM(DrawBitmapRect2GM.Variant.INT), "DrawBitmapRect2GM_int")
    }

    private fun runVariant(gm: DrawBitmapRect2GM, label: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(label, comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(label, comparison.similarity)
        assertTrue(accepted, "$label regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "$label similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)",
        )
    }
}
