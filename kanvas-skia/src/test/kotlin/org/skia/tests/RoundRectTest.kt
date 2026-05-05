package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RoundRectTest {
    @Test
    fun `RoundRectGM matches roundrects_png within tolerance`() {
        val gm = RoundRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image roundrects.png")
        // 5×8 grid of `setRectXY(5, 5)` rrects under all paint × matrix
        // combinations + 7 special-case rows (tall / wide / skinny / short /
        // gradient / strokes-and-radii / OOO / stroke>radius/2). Same
        // gradient-column drift as OvalGM.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RoundRectGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RoundRectGM", comparison.similarity)
        assertTrue(accepted, "RoundRectGM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "RoundRectGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (gradient column missing)")
    }
}
