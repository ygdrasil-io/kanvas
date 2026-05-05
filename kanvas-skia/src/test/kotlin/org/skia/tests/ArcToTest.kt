package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ArcToTest {

    @Test
    fun `ArcToGM matches arcto_png within tolerance`() {
        val gm = ArcToGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image arcto.png")
        // ArcToGM exercises three SVG-arc code paths end-to-end:
        //  - the loop section (8 dark-red arcs with rotation + ellipse),
        //  - the four-coloured chord section (each (largeArc, sweep)
        //    permutation),
        //  - the zero-length round-cap section (two degenerate arcs).
        // The first two ride on the SVG endpoint-to-conic conversion
        // (Phase 3.7); the third validates the degenerate fall-throughs.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ArcToGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ArcToGM", comparison.similarity)
        assertTrue(accepted, "ArcToGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "ArcToGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%",
        )
    }
}
