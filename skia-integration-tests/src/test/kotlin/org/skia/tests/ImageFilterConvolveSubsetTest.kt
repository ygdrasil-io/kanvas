package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFilterConvolveSubsetTest {

    @Test
    fun `ImageFilterConvolveSubsetGM matches imagefilter_convolve_subset_png within tolerance`() {
        val gm = ImageFilterConvolveSubsetGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefilter_convolve_subset.png")

        // MatrixConvolution (3×3 sharpen, kClamp, crop) + Blur (σ=10,
        // kMirror, crop) over filter_reference.png. Pixel-level rounding
        // differences between the Kotlin raster path and upstream Skia's
        // SIMD-optimised convolution warrant a moderate tolerance.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 12)
        TestReport.recordDetailed("ImageFilterConvolveSubsetGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFilterConvolveSubsetGM", comparison.similarity)
        assertTrue(accepted, "ImageFilterConvolveSubsetGM regressed below ratchet")
    }
}
