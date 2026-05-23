package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RRectDrawBwTest {

    @Test
    fun `RRectDrawBwGM matches rrect_draw_bw png within tolerance`() {
        val gm = RRectDrawBwGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rrect_draw_bw.png")
        // 43 rrects (7 simple + 35 complex + 1 big clipped) drawn BW-filled
        // in an 80×40 tile grid. No AA — hard edges only.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RRectDrawBwGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RRectDrawBwGM", comparison.similarity)
        assertTrue(accepted, "RRectDrawBwGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "RRectDrawBwGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
