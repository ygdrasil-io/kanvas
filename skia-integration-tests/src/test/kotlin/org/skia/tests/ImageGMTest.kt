package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageGMTest {

    @Test
    fun `ImageGM matches reference within tolerance`() {
        val gm = ImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("ImageGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("ImageGM", comparison.similarity)
        assertTrue(accepted, "ImageGM regressed below ratchet")
    }
}
