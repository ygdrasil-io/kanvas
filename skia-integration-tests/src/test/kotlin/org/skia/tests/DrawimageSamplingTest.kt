package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawimageSamplingTest {

    @Test
    fun `DrawimageSamplingGM matches reference within tolerance`() {
        val gm = DrawimageSamplingGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("DrawimageSamplingGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("DrawimageSamplingGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("DrawimageSamplingGM", comparison.similarity)
        assertTrue(accepted, "DrawimageSamplingGM regressed below ratchet")
    }
}
