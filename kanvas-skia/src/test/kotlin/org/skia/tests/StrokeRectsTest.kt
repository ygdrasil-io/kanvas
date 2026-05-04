package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StrokeRectsTest {
    @Test
    fun `StrokeRectsGM matches strokerects_png within tolerance`() {
        val gm = StrokeRectsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image strokerects.png")
        // 4 panes × 100 random rects each. AA off / on × strokeWidth 0 / 3.
        // The strokeWidth=0 + non-AA pane uses our hairline fallback
        // (width=1), drifting from upstream's true hairline scan-line.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("StrokeRectsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokeRectsGM", comparison.similarity)
        assertTrue(accepted, "StrokeRectsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "StrokeRectsGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% (hairline fallback)")
    }
}
