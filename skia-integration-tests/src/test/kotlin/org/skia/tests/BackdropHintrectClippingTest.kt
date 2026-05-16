package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BackdropHintrectClippingTest {
    @Test
    fun `BackdropHintrectClippingGM matches backdrop_hintrect_clipping_png within tolerance`() {
        val gm = BackdropHintrectClippingGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image backdrop_hintrect_clipping.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BackdropHintrectClippingGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BackdropHintrectClippingGM", comparison.similarity)
        assertTrue(accepted, "BackdropHintrectClippingGM regressed below ratchet")
        assertTrue(comparison.similarity >= EXPECTED_SIMILARITY,
            "BackdropHintrectClippingGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%")
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 60.0
    }
}
