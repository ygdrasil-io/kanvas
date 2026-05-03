package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawBitmapRect3Test {

    @Test
    fun `DrawBitmapRect3 matches 3x3bitmaprect_png within tolerance`() {
        val gm = DrawBitmapRect3()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image 3x3bitmaprect.png")
        // Rendered into the DM reference colorspace (Rec.2020), so the source
        // image is xformed sRGB → Rec.2020 once at the top of `drawImageRect`
        // and sampled from the converted buffer. tolerance=1 catches anything
        // beyond rounding noise.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawBitmapRect3", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawBitmapRect3", comparison.similarity)
        assertTrue(accepted, "DrawBitmapRect3 regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "DrawBitmapRect3 similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=1 floor)")
    }
}
