package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageSubsetTest {

    @Test
    fun `ImageSubsetGM matches reference within tolerance`() {
        val gm = ImageSubsetGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("ImageSubsetGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageSubsetGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("ImageSubsetGM", comparison.similarity)
        assertTrue(accepted, "ImageSubsetGM regressed below ratchet")
    }
}
