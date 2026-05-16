package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [ShallowGradientRadialGM] — the upstream
 * `shallow_gradient_radial` (with-dither) GM. Our raster pipeline
 * does not currently inject dither, so this matches the no-dither
 * reference perfectly and the with-dither one within ≤1 LSB / channel.
 */
class ShallowGradientRadialTest {

    @Test
    fun `ShallowGradientRadialGM matches shallow_gradient_radial_png within tolerance`() {
        val gm = ShallowGradientRadialGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image shallow_gradient_radial.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("ShallowGradientRadialGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ShallowGradientRadialGM", comparison.similarity)
        assertTrue(accepted, "ShallowGradientRadialGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "ShallowGradientRadialGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=2 floor)",
        )
    }
}
