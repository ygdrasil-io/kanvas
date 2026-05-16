package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PictureImageFilterTest {
    @Test
    fun `PictureImageFilterGM matches pictureimagefilter_png within tolerance`() {
        val gm = PictureImageFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pictureimagefilter.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("PictureImageFilterGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("PictureImageFilterGM", comparison.similarity)
        assertTrue(accepted, "PictureImageFilterGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "PictureImageFilterGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%",
        )
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 62.0
    }
}
