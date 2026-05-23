package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LocalMatrixImageShaderFilteringTest {

    @Test
    fun `LocalMatrixImageShaderFilteringGM matches localmatriximageshader_filtering_png within tolerance`() {
        val gm = LocalMatrixImageShaderFilteringGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image localmatriximageshader_filtering.png")
        // Mitchell-bicubic shader with a 2x scale local matrix applied to a 256x256
        // mandrill image drawn into a 256x256 canvas. The scaling in the local matrix
        // effectively zooms in on the top-left quadrant of the image with cubic
        // interpolation — testing that bicubic quality is selected from the local matrix.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("LocalMatrixImageShaderFilteringGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LocalMatrixImageShaderFilteringGM", comparison.similarity)
        assertTrue(accepted, "LocalMatrixImageShaderFilteringGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "LocalMatrixImageShaderFilteringGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
