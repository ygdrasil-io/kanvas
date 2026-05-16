package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersStrokedTest {

    @Test
    fun `ImageFiltersStrokedGM matches imagefiltersstroked_png within tolerance`() {
        val gm = ImageFiltersStrokedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefiltersstroked.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("ImageFiltersStrokedGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersStrokedGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersStrokedGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 5.0,
            "ImageFiltersStrokedGM similarity ${"%.2f".format(comparison.similarity)}% < 5.0% floor",
        )
    }
}
