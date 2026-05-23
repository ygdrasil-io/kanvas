package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersEffectOrderTest {

    @Test
    fun `ImageFiltersEffectOrderGM matches reference`() {
        val gm = ImageFiltersEffectOrderGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ImageFiltersEffectOrderGM", comparison)
        if (comparison.similarity < FLOOR) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersEffectOrderGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersEffectOrderGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= FLOOR,
            "ImageFiltersEffectOrderGM similarity ${"%.2f".format(comparison.similarity)}% < $FLOOR%",
        )
    }

    private companion object {
        private const val FLOOR: Double = 44.0
    }
}
