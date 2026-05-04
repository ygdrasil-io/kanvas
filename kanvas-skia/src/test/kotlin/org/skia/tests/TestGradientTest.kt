package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TestGradientTest {

    @Test
    fun `TestGradientGM matches testgradient_png within tolerance`() {
        val gm = TestGradientGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image testgradient.png")
        // Mixed gradient rect + filled oval + circle + stroked roundrect.
        // Gradient rect samples a sub-segment of the [0..1] interval, so
        // sub-pixel drift on the linear lookup vs upstream's float lerp
        // is the dominant residual.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TestGradientGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TestGradientGM", comparison.similarity)
        assertTrue(accepted, "TestGradientGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "TestGradientGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)")
    }
}
