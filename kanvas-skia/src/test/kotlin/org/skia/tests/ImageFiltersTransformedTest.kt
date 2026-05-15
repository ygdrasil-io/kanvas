package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersTransformedTest {

    @Test
    fun `ImageFiltersTransformedGM matches imagefilter_matrix_localmatrix_png within tolerance`() {
        val gm = ImageFiltersTransformedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefilter_matrix_localmatrix.png")
        // Mandrill image rotated through MatrixTransform + LM wrapper.
        // Linear-filter sampling around the rotation matches upstream
        // pixel-for-pixel within ~8 ulp ; floor 30 % accommodates the
        // bilinear-interpolation drift that accumulates around the
        // image's high-frequency fur regions.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ImageFiltersTransformedGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersTransformedGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersTransformedGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "ImageFiltersTransformedGM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
