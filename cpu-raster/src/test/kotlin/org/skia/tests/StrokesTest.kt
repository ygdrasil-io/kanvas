package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StrokesTest {

    @Test
    fun `StrokesGM matches strokes_round_png within tolerance`() {
        val gm = StrokesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image strokes_round.png")
        // 50 random oval + 50 random roundrect pairs per pane (AA off / AA on),
        // plus the per-pane 2-px clipRect inset. Random colours and rect
        // positions match upstream because SkRandom is bit-compatible.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("StrokesGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokesGM", comparison.similarity)
        assertTrue(accepted, "StrokesGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "StrokesGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)")
    }
}
