package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase GM-port runner for [BlurImageVMaskGM].
 *
 * Side-by-side comparison of mask blur vs image-filter blur over five
 * sigmas. Mask blur and image-filter blur use slightly different
 * Gaussian discretisations between us and upstream (`SkRasterPipeline`
 * vs our convolution), so we allow a moderate tolerance and floor at
 * 85% similarity.
 */
class BlurImageVMaskTest {

    @Test
    fun `BlurImageVMaskGM matches blurimagevmask_png within tolerance`() {
        val gm = BlurImageVMaskGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blurimagevmask.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BlurImageVMaskGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurImageVMaskGM", comparison.similarity)
        assertTrue(accepted, "BlurImageVMaskGM regressed below ratchet")
    }
}
