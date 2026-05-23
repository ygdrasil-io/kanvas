package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RRectDrawAaTest {

    @Test
    fun `RRectDrawAaGM matches rrect_draw_aa png within tolerance`() {
        val gm = RRectDrawAaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rrect_draw_aa.png")
        // 43 rrects drawn AA-filled in an 80×40 tile grid. Anti-alias produces
        // smooth subpixel edges on each rrect corner arc.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RRectDrawAaGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RRectDrawAaGM", comparison.similarity)
        assertTrue(accepted, "RRectDrawAaGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "RRectDrawAaGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0%")
    }
}
