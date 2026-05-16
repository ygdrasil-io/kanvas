package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DashCircleTest {
    @Test
    fun `DashCircleGM matches dashcircle_png within tolerance`() {
        val gm = DashCircleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image dashcircle.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DashCircleGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DashCircleGM", comparison.similarity)
        assertTrue(accepted, "DashCircleGM regressed below ratchet")
        assertTrue(comparison.similarity >= 75.0,
            "DashCircleGM similarity ${"%.2f".format(comparison.similarity)}% < 75.0%")
    }
}
