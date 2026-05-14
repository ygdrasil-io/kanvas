package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TallStretchedBitmapsTest {

    @Test
    fun `TallStretchedBitmapsGM matches reference`() {
        val gm = TallStretchedBitmapsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TallStretchedBitmapsGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TallStretchedBitmapsGM", comparison.similarity)
        assertTrue(accepted, "TallStretchedBitmapsGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= FLOOR,
            "TallStretchedBitmapsGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR%",
        )
    }

    private companion object {
        // Observed ~87.95% with tolerance=1 ; floor = actual − ~0.1.
        private const val FLOOR: Double = 87.8
    }
}
