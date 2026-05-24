package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SaveLayerWithBackdropTest {

    @Test
    fun `SaveLayerWithBackdropGM matches savelayer_with_backdrop_png within tolerance`() {
        val gm = SaveLayerWithBackdropGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image savelayer_with_backdrop.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("SaveLayerWithBackdropGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("SaveLayerWithBackdropGM", comparison.similarity)
        assertTrue(accepted, "SaveLayerWithBackdropGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "SaveLayerWithBackdropGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY% floor",
        )
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 70.0
    }
}
