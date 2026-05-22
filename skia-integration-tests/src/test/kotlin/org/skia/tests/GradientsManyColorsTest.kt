package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class GradientsManyColorsTest {

    @Test
    fun `GradientsManyColorsGM matches reference within tolerance`() {
        val gm = GradientsManyColorsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("GradientsManyColorsGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("GradientsManyColorsGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("GradientsManyColorsGM", comparison.similarity)
        assertTrue(accepted, "GradientsManyColorsGM regressed below ratchet")
    }
}
