package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawMiniBitmapRectTest {
    @Test
    @Disabled("SLOW.GM_STRESS: ~3 min in the default ratchet run; run this class explicitly before changing mini-bitmap sampling.")
    fun `DrawMiniBitmapRectGM matches drawminibitmaprect_png within tolerance`() {
        val gm = DrawMiniBitmapRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drawminibitmaprect.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawMiniBitmapRectGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawMiniBitmapRectGM", comparison.similarity)
        assertTrue(accepted, "DrawMiniBitmapRectGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 40.0,
            "DrawMiniBitmapRectGM similarity ${"%.2f".format(comparison.similarity)}% < 40.0%",
        )
    }
}
