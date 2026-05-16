package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BackdropImagefilterCroprectTest {
    @Test
    fun `BackdropImagefilterCroprectGM matches backdrop_imagefilter_croprect_png within tolerance`() {
        val gm = BackdropImagefilterCroprectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image backdrop_imagefilter_croprect.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BackdropImagefilterCroprectGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BackdropImagefilterCroprectGM", comparison.similarity)
        assertTrue(accepted, "BackdropImagefilterCroprectGM regressed below ratchet")
        assertTrue(comparison.similarity >= EXPECTED_SIMILARITY,
            "BackdropImagefilterCroprectGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%")
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 90.0
    }
}
