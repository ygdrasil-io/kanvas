package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BitmapCopyTest {
    @Test
    fun `BitmapCopyGM matches bitmapcopy_png within tolerance`() {
        val gm = BitmapCopyGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bitmapcopy.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BitmapCopyGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("BitmapCopyGM", comparison.similarity)
        assertTrue(accepted, "BitmapCopyGM regressed below ratchet")
        assertTrue(comparison.similarity >= EXPECTED_SIMILARITY,
            "BitmapCopyGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%")
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 99.0
    }
}
