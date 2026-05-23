package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RRectClipAaTest {

    @Test
    fun `RRectClipAaGM matches rrect_clip_aa png within tolerance`() {
        val gm = RRectClipAaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rrect_clip_aa.png")
        // 43 rrects used as AA clip regions; each cell is filled with a
        // black→yellow linear gradient after setMatrix(Scale(w,h)).
        // AA clipping produces soft edges on the rrect boundary.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RRectClipAaGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RRectClipAaGM", comparison.similarity)
        assertTrue(accepted, "RRectClipAaGM regressed below ratchet")
        assertTrue(comparison.similarity >= FLOOR,
            "RRectClipAaGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR%")
    }

    private companion object {
        private const val FLOOR: Double = 60.0
    }
}
