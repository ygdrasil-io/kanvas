package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [ShallowGradientLinearGM] — the upstream
 * `shallow_gradient_linear` (with-dither) GM. Our raster pipeline
 * does not currently inject dither, so this matches the no-dither
 * reference perfectly and the with-dither one within ≤1 LSB / channel.
 */
class ShallowGradientLinearTest {

    @Test
    fun `ShallowGradientLinearGM matches shallow_gradient_linear_png within tolerance`() {
        val gm = ShallowGradientLinearGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image shallow_gradient_linear.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("ShallowGradientLinearGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShallowGradientLinearGM", comparison.similarity)
        assertTrue(accepted, "ShallowGradientLinearGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "ShallowGradientLinearGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=2 floor)",
        )
    }
}
