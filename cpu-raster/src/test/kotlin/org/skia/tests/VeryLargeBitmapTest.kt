package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class VeryLargeBitmapTest {
    @Test
    fun `VeryLargeBitmapGM matches verylargebitmap_png within tolerance`() {
        val gm = VeryLargeBitmapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image verylargebitmap.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("VeryLargeBitmapGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("VeryLargeBitmapGM", comparison.similarity)
        assertTrue(accepted, "VeryLargeBitmapGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "VeryLargeBitmapGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%",
        )
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 94.0
    }
}
