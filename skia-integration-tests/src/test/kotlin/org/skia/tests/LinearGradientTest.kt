package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LinearGradientTest {
    @Test
    fun `LinearGradientGM dither matches linear_gradient_png within tolerance`() {
        val gm = LinearGradientGM(fDither = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image linear_gradient.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LinearGradientGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LinearGradientGM", comparison.similarity)
        assertTrue(accepted, "LinearGradientGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "LinearGradientGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }

    @Test
    fun `LinearGradientGM nodither matches linear_gradient_nodither_png within tolerance`() {
        val gm = LinearGradientNoDitherGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image linear_gradient_nodither.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LinearGradientNoDitherGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LinearGradientNoDitherGM", comparison.similarity)
        assertTrue(accepted, "LinearGradientNoDitherGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "LinearGradientNoDitherGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
