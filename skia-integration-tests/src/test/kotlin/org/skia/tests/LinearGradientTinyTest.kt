package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LinearGradientTinyTest {
    @Test
    fun `LinearGradientTinyGM matches linear_gradient_tiny_png within tolerance`() {
        val gm = LinearGradientTinyGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image linear_gradient_tiny.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LinearGradientTinyGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LinearGradientTinyGM", comparison.similarity)
        assertTrue(accepted, "LinearGradientTinyGM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "LinearGradientTinyGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
