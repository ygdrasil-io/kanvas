package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [ContourStartGM] — the upstream `contour_start` GM.
 * 1200 × 600 canvas with 5 families × 16 sub-paths each (8 start
 * indices × 2 directions), dash-stroked with a geometric-progression
 * interval pattern and overlaid with 3-px maroon points at every
 * stored coord. Tests that
 * `SkPath::{Rect,Oval,RRect}(rect, dir, startIndex)` factories all
 * emit the same set of coords as upstream — if any of them rotates
 * the starting corner differently from upstream, the dash phase
 * shifts visibly.
 */
class ContourStartTest {

    @Test
    fun `ContourStartGM matches contour_start_png within tolerance`() {
        val gm = ContourStartGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image contour_start.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("ContourStartGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ContourStartGM", comparison.similarity)
        assertTrue(accepted, "ContourStartGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 70.0,
            "ContourStartGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
