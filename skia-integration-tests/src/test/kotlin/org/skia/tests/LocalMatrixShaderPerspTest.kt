package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LocalMatrixShaderPerspTest {

    @Test
    fun `LocalMatrixShaderPerspGM matches localmatrixshader_persp_png within tolerance`() {
        val gm = LocalMatrixShaderPerspGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image localmatrixshader_persp.png")
        // Two rows of four perspective-vs-matrix variants. The dominant
        // drift is in the perspective-reprojected cells (variant 1/3) where
        // bilinear scaling of our 128×128 downscale differs slightly from
        // upstream's `scalePixels`. Tolerance 12 covers the edge AA and
        // sub-pixel reprojection differences while catching regressions.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 12)
        TestReport.recordDetailed("LocalMatrixShaderPerspGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LocalMatrixShaderPerspGM", comparison.similarity)
        assertTrue(accepted, "LocalMatrixShaderPerspGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "LocalMatrixShaderPerspGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
