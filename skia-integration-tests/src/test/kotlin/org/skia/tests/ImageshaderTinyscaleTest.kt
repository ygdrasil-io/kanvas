package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageshaderTinyscaleTest {

    @Test
    fun `ImageshaderTinyscaleGM matches reference within tolerance`() {
        val gm = ImageshaderTinyscaleGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("ImageshaderTinyscaleGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("ImageshaderTinyscaleGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("ImageshaderTinyscaleGM", comparison.similarity)
        assertTrue(accepted, "ImageshaderTinyscaleGM regressed below ratchet")
    }
}
