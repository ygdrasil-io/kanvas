package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SmallArcTest {
    @Test
    fun `SmallArcGM matches smallarc_png within tolerance`() {
        val gm = SmallArcGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image smallarc.png")
        // Single AA cubic stroked at width 120 under scale(8, 8). Stresses
        // the resScale-aware stroker on a curve at moderate CTM scale.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("SmallArcGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SmallArcGM", comparison.similarity)
        assertTrue(accepted, "SmallArcGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "SmallArcGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)")
    }
}
