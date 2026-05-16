package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClampedGradientsTest {
    @Test
    fun `ClampedGradientsGM matches clamped_gradients_png within tolerance`() {
        val gm = ClampedGradientsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clamped_gradients.png")
        // Single radial gradient (red→green→blue→white→black) with centre
        // outside the drawn rect — every pixel exercises the radial
        // distance lookup.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ClampedGradientsGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClampedGradientsGM", comparison.similarity)
        assertTrue(accepted, "ClampedGradientsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 93.0,
            "ClampedGradientsGM similarity ${"%.2f".format(comparison.similarity)}% < 93.0%")
    }
}
