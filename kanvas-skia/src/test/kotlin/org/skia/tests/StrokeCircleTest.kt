package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StrokeCircleTest {
    @Test
    fun `StrokeCircleGM matches strokecircle_png within tolerance`() {
        val gm = StrokeCircleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image strokecircle.png")
        // Concentric stroked ovals at 20× CTM scale (resScale path).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("StrokeCircleGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokeCircleGM", comparison.similarity)
        assertTrue(accepted, "StrokeCircleGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "StrokeCircleGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
