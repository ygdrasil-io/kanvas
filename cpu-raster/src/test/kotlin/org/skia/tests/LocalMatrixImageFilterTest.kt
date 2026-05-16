package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Regression test for [LocalMatrixImageFilterGM] (R-final.2).
 *
 * Renders the GM into the DM-reference colour space and compares
 * pixel-by-pixel against `original-888/localmatriximagefilter.png`.
 * Threshold floor is generous (40 %) — the GM stresses morphology +
 * blur + offset filter pipelines under three local matrices, all of
 * which differ by sub-pixel quantisation between our raster path and
 * upstream's GPU-augmented reference. Any regression below the
 * ratchet is still flagged via [SimilarityTracker].
 */
class LocalMatrixImageFilterTest {

    @Test
    fun `LocalMatrixImageFilterGM matches localmatriximagefilter_png within tolerance`() {
        val gm = LocalMatrixImageFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image localmatriximagefilter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("LocalMatrixImageFilterGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LocalMatrixImageFilterGM", comparison.similarity)
        assertTrue(accepted, "LocalMatrixImageFilterGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 40.0,
            "LocalMatrixImageFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 40% floor",
        )
    }
}
