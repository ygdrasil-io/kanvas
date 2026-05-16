package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AnisotropicImageScaleAnisoTest {

    @Test
    fun `AnisotropicImageScaleAnisoGM matches anisotropic_image_scale_aniso_png within tolerance`() {
        val gm = AnisotropicImageScaleAnisoGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image anisotropic_image_scale_aniso.png")
        // Same grid as the linear / mip variants, but with the
        // [SkSamplingOptions.Aniso] sampler (N=16 taps along the
        // texture-space major axis). The kanvas-skia implementation is
        // an N-tap shortcut — full elliptical weighted average kernels
        // aren't ported — so floor sits at 25% for the residual.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AnisotropicImageScaleAnisoGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnisotropicImageScaleAnisoGM", comparison.similarity)
        assertTrue(accepted, "AnisotropicImageScaleAnisoGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 25.0,
            "AnisotropicImageScaleAnisoGM similarity ${"%.2f".format(comparison.similarity)}% < 25.0% (t=8 floor)",
        )
    }
}
