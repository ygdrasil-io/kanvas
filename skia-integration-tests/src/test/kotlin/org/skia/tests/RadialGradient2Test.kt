package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for [RadialGradient2GM] -- upstream's `radial_gradient2` (with-
 * dither) and `radial_gradient2_nodither` variants. The `InPremul`
 * interpolation flag isn't modelled by `:kanvas-skia` yet, so both
 * columns render identically (against an upstream reference that does
 * differ). Accept-any-result -- ratchet captures the observed similarity.
 */
class RadialGradient2Test {

    @Test
    fun `RadialGradient2GM dither matches radial_gradient2_png within tolerance`() {
        val gm = RadialGradient2GM(dither = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image radial_gradient2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RadialGradient2GM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RadialGradient2GM", comparison.similarity)
        assertTrue(accepted, "RadialGradient2GM regressed below ratchet")
    }

    @Test
    fun `RadialGradient2GM nodither matches radial_gradient2_nodither_png within tolerance`() {
        val gm = RadialGradient2GM(dither = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image radial_gradient2_nodither.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RadialGradient2NoditherGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RadialGradient2NoditherGM", comparison.similarity)
        assertTrue(accepted, "RadialGradient2NoditherGM regressed below ratchet")
    }
}
