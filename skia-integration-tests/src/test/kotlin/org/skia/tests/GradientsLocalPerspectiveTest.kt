package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientsLocalPerspectiveTest {

    @Test
    fun `GradientsLocalPerspectiveGM matches reference within tolerance`() {
        val gm = GradientsLocalPerspectiveGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("GradientsLocalPerspectiveGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("GradientsLocalPerspectiveGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("GradientsLocalPerspectiveGM", comparison.similarity)
        assertTrue(accepted, "GradientsLocalPerspectiveGM regressed below ratchet")
    }
}
