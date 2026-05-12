package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AnisotropicImageScaleMipTest {

    @Test
    fun `AnisotropicImageScaleMipGM matches anisotropic_image_scale_mip_png within tolerance`() {
        val gm = AnisotropicImageScaleMipGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image anisotropic_image_scale_mip.png")
        // 522 × 1330 grey-cleared canvas. Same source + grid layout as
        // the _linear variant ; difference is the bilinear-on-mip
        // sampler. kanvas-skia uses box-filtered mip generation and
        // floor-LOD selection — upstream's raster picks LOD with a
        // bias and uses trilinear blending between levels. Floor is
        // 30% : the radial-line input plus per-level alignment drift
        // adds a few percent of disagreement on top of the mip-vs-mip
        // residual.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AnisotropicImageScaleMipGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnisotropicImageScaleMipGM", comparison.similarity)
        assertTrue(accepted, "AnisotropicImageScaleMipGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "AnisotropicImageScaleMipGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% (t=8 floor)",
        )
    }
}
