package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LargeClippedPathWindingTest {

    @Test
    fun `LargeClippedPathWindingGM matches largeclippedpath_winding_png within tolerance`() {
        val gm = LargeClippedPathWindingGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image largeclippedpath_winding.png")
        // 1000 × 1000 cyan-cleared canvas with a 50 × 50 grid `clipPath` and a 9-petal
        // flower drawn over the unioned-grid region under the kWinding fill rule. The
        // bulk of the canvas is the AA-rasterised grid seams + the magenta flower
        // interior — a coarse tolerance (t=2) accommodates the linear-to-Rec.2020
        // colour-space round-trip on the flower's pure SK_ColorMAGENTA fill.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("LargeClippedPathWindingGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LargeClippedPathWindingGM", comparison.similarity)
        assertTrue(accepted, "LargeClippedPathWindingGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "LargeClippedPathWindingGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=2 floor)",
        )
    }
}
