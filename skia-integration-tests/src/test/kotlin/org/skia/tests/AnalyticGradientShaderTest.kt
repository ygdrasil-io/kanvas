package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AnalyticGradientShaderTest {
    @Test
    fun `AnalyticGradientShaderGM matches analytic_gradients_png within tolerance`() {
        val gm = AnalyticGradientShaderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image analytic_gradients.png")
        // 8 × 4 grid of linear gradients with hardstops. Stresses
        // SkLinearGradient binary search across stop counts 2..16 plus
        // duplicate-position handling.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("AnalyticGradientShaderGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnalyticGradientShaderGM", comparison.similarity)
        assertTrue(accepted, "AnalyticGradientShaderGM regressed below ratchet")
        assertTrue(comparison.similarity >= 60.0,
            "AnalyticGradientShaderGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0%")
    }
}
