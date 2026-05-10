package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurTextSmallRadiiTest {

    @Test
    fun `BlurTextSmallRadiiGM matches blurSmallRadii_png within tolerance`() {
        val gm = BlurTextSmallRadiiGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blurSmallRadii.png")
        // 6 rows of "guest" text at small blur sigmas (0.25..2.5). Text
        // shaping + small-σ blur kernels are sensitive to font-driver
        // glyph metrics; we floor at 85% to allow for minor anti-alias
        // ramp drift and font-rendering deltas.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurTextSmallRadiiGM", comparison)
        if (comparison.similarity < 85.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurTextSmallRadiiGM", comparison.similarity)
        assertTrue(accepted, "BlurTextSmallRadiiGM regressed below ratchet")
        assertTrue(comparison.similarity >= 85.0,
            "BlurTextSmallRadiiGM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% (t=1 floor)")
    }
}
