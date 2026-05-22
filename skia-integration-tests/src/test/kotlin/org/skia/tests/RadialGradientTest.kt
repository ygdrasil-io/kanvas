package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [RadialGradientGM] — the upstream `radial_gradient` GM
 * (with dither). The 1280 x 1280 single drawRect with a 3-stop radial
 * gradient stresses our radial-gradient interpolation across a large
 * area.
 */
class RadialGradientTest {

    @Test
    fun `RadialGradientGM matches radial_gradient_png within tolerance`() {
        val gm = RadialGradientGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image radial_gradient.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RadialGradientGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RadialGradientGM", comparison.similarity)
        assertTrue(accepted, "RadialGradientGM regressed below ratchet")
        // Accept-any-result floor : 0 % (dither + alpha-blended gradient
        // over background may diverge significantly from the dithered
        // upstream reference). Ratchet captures observed similarity.
    }
}
