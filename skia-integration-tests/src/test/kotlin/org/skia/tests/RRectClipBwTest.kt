package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RRectClipBwTest {

    @Test
    fun `RRectClipBwGM matches rrect_clip_bw png within tolerance`() {
        val gm = RRectClipBwGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rrect_clip_bw.png")
        // 43 rrects used as non-AA clip regions; each cell is filled with a
        // black→yellow linear gradient after setMatrix(Scale(w,h)) to verify
        // local-coordinate preservation through the clip.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RRectClipBwGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RRectClipBwGM", comparison.similarity)
        assertTrue(accepted, "RRectClipBwGM regressed below ratchet")
        assertTrue(comparison.similarity >= FLOOR,
            "RRectClipBwGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR%")
    }

    private companion object {
        private const val FLOOR: Double = 60.0
    }
}
