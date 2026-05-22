package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageMakeWithFilterTest {

    @Test
    fun `ImageMakeWithFilterGM matches reference within tolerance`() {
        val gm = ImageMakeWithFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("ImageMakeWithFilterGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("ImageMakeWithFilterGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("ImageMakeWithFilterGM", comparison.similarity)
        assertTrue(accepted, "ImageMakeWithFilterGM regressed below ratchet")
    }
}
