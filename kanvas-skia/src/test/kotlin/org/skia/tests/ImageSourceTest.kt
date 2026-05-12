package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageSourceTest {

    @Test
    fun `ImageSourceGM matches imagesource_png within tolerance`() {
        val gm = ImageSourceGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagesource.png")
        // Filter chain output through clipRect — Image-source filter
        // uses cubic resampler in 3/4 panels, so edge interpolation
        // differs by a few ulps from upstream. Tolerance 8 matches our
        // other filter-residual GMs.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ImageSourceGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageSourceGM", comparison.similarity)
        assertTrue(accepted, "ImageSourceGM regressed below ratchet")
    }
}
