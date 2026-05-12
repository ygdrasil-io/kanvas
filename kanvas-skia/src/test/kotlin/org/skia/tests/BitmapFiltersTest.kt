package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BitmapFiltersTest {
    @Test
    fun `BitmapFiltersGM matches bitmapfilters_png within tolerance`() {
        val gm = BitmapFiltersGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bitmapfilters.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BitmapFiltersGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BitmapFiltersGM", comparison.similarity)
        assertTrue(accepted, "BitmapFiltersGM regressed below ratchet")
        assertTrue(comparison.similarity >= EXPECTED_SIMILARITY,
            "BitmapFiltersGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%")
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 60.0
    }
}
