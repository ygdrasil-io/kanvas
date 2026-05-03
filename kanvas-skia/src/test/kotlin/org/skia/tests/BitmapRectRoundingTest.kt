package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BitmapRectRoundingTest {

    @Test
    fun `BitmapRectRoundingGM matches bitmaprect_rounding_png within tolerance`() {
        val gm = BitmapRectRoundingGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bitmaprect_rounding.png")
        // Subpixel scale + drawImageRect over a red drawRect. Output should
        // be a single solid blue rectangle. tolerance=1 catches the
        // colorspace-converted blue band exactly.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BitmapRectRoundingGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BitmapRectRoundingGM", comparison.similarity)
        assertTrue(accepted, "BitmapRectRoundingGM regressed below ratchet")
        assertTrue(comparison.similarity >= 99.0,
            "BitmapRectRoundingGM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% (t=1 floor)")
    }
}
