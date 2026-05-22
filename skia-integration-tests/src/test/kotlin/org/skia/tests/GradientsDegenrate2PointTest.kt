package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientsDegenrate2PointTest {

    @Test
    fun `GradientsDegenrate2PointGM matches reference within tolerance`() {
        val gm = GradientsDegenrate2PointGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            // Reference missing — register baseline at 0 % so the ratchet
            // tracks the GM ; the test always passes (accept-any-result).
            SimilarityTracker.updateScore("GradientsDegenrate2PointGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("GradientsDegenrate2PointGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("GradientsDegenrate2PointGM", comparison.similarity)
        assertTrue(accepted, "GradientsDegenrate2PointGM regressed below ratchet")
    }
}
