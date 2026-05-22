package org.skia.tests

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImagePictTest {

    @Test
    fun `ImagePictGM matches reference within tolerance`() {
        val gm = ImagePictGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        if (reference == null) {
            SimilarityTracker.updateScore("ImagePictGM", 0.0)
            return
        }
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference, tolerance = 8)
        TestReport.recordDetailed("ImagePictGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("ImagePictGM", comparison.similarity)
        assertTrue(accepted, "ImagePictGM regressed below ratchet")
    }
}
