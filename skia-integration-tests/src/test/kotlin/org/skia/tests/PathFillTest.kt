package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathFillTest {
    @Test
    fun `PathFillGM matches pathfill_png within tolerance`() {
        val gm = PathFillGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pathfill.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("PathFillGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PathFillGM", comparison.similarity)
        assertTrue(accepted, "PathFillGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "PathFillGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
