package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AnisotropicImageScaleLinearTest {

    @Test
    fun `AnisotropicImageScaleLinearGM matches anisotropic_image_scale_linear_png within tolerance`() {
        val gm = AnisotropicImageScaleLinearGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image anisotropic_image_scale_linear.png")
        // 522 × 1330 grey-cleared canvas with a 100-ray radial source image
        // sampled into 18 progressively-squashed rect targets under bilinear
        // filtering. Floor sits low (50 %) since kanvas-skia's hairline
        // strokeWidth=0 is not strictly identical to upstream's
        // `drawPoints(kLines)` stroke quantum and we don't yet model
        // mipmaps — the minification taps a single LOD and aliases the
        // radial-line input under high downscales.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 6)
        TestReport.recordDetailed("AnisotropicImageScaleLinearGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnisotropicImageScaleLinearGM", comparison.similarity)
        assertTrue(accepted, "AnisotropicImageScaleLinearGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "AnisotropicImageScaleLinearGM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% (t=6 floor)",
        )
    }
}
