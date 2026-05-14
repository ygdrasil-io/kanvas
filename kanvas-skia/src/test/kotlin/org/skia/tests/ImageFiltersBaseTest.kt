package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageFiltersBaseTest {
    @Test
    fun `ImageFiltersBaseGM matches imagefiltersbase_png within tolerance`() {
        val gm = ImageFiltersBaseGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imagefiltersbase.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageFiltersBaseGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersBaseGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersBaseGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 85.7,
            "ImageFiltersBaseGM similarity ${"%.2f".format(comparison.similarity)}% < 85.7% floor",
        )
    }
}
