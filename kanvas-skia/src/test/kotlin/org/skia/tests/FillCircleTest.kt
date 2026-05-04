package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class FillCircleTest {
    @Test
    fun `FillCircleGM matches fillcircle_png within tolerance`() {
        val gm = FillCircleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fillcircle.png")
        // Concentric ovals filled with 565-quantised random colours under
        // scale(20, 20). Stresses big-radius oval rasterizer fill paths.
        // No rotate (fRotate = 0 in static GM dump).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("FillCircleGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FillCircleGM", comparison.similarity)
        assertTrue(accepted, "FillCircleGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "FillCircleGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% (t=1 floor)")
    }
}
