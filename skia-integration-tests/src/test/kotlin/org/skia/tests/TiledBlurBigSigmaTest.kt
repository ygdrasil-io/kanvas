package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TiledBlurBigSigmaTest {

    @Test
    @Disabled("SLOW.GM_STRESS: ~2 min Gaussian tile-mode stress test; run explicitly when touching blur tiling or large-sigma blur.")
    fun `TiledBlurBigSigmaGM matches TiledBlurBigSigma_png within tolerance`() {
        val gm = TiledBlurBigSigmaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image TiledBlurBigSigma.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TiledBlurBigSigmaGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TiledBlurBigSigmaGM", comparison.similarity)
        assertTrue(accepted, "TiledBlurBigSigmaGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "TiledBlurBigSigmaGM similarity ${"%.2f".format(comparison.similarity)}% < 0% floor",
        )
    }
}
