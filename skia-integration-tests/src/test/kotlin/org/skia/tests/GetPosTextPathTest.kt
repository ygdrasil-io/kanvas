package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GetPosTextPathTest {

    @Test
    fun `GetPosTextPathGM matches getpostextpath_png within tolerance`() {
        val gm = GetPosTextPathGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image getpostextpath.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("GetPosTextPathGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("GetPosTextPathGM", comparison.similarity)
        assertTrue(accepted, "GetPosTextPathGM regressed below ratchet")
        // Text path stroking + reference rendered by upstream Skia at the
        // same advance widths but Liberation typefaces are not pixel-
        // identical to Skia's portable test font ; we floor at 70%.
        assertTrue(comparison.similarity >= 70.0,
            "GetPosTextPathGM similarity ${"%.2f".format(comparison.similarity)}% < 70% (t=16 floor)")
    }
}
