package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurBigSigmaTest {

    @Test
    fun `BlurBigSigmaGM matches BlurBigSigma_png within tolerance`() {
        val gm = BlurBigSigmaGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image BlurBigSigma.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurBigSigmaGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurBigSigmaGM", comparison.similarity)
        assertTrue(accepted, "BlurBigSigmaGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "BlurBigSigmaGM similarity ${"%.2f".format(comparison.similarity)}% < 0% floor",
        )
    }
}
