package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientsViewPerspectiveTest {

    @Test
    fun `GradientsViewPerspectiveGM matches reference within tolerance`() {
        val gm = GradientsViewPerspectiveGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("GradientsViewPerspectiveGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("GradientsViewPerspectiveGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("GradientsViewPerspectiveGM", comparison.similarity)
        assertTrue(accepted, "GradientsViewPerspectiveGM regressed below ratchet")
    }
}
