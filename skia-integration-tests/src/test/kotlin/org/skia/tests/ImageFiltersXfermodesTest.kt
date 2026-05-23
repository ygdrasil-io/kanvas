package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersXfermodesTest {

    @Test
    fun `ImageFiltersXfermodesGM matches reference`() {
        val gm = ImageFiltersXfermodesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ImageFiltersXfermodesGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersXfermodesGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersXfermodesGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= FLOOR,
            "ImageFiltersXfermodesGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR%",
        )
    }

    private companion object {
        private const val FLOOR: Double = 85.0
    }
}
