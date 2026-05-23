package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageBlurRepeatUnclippedTest {

    @Test
    fun `ImageBlurRepeatUnclippedGM matches imageblurrepeatunclipped_png within tolerance`() {
        val gm = ImageBlurRepeatUnclippedGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imageblurrepeatunclipped.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ImageBlurRepeatUnclippedGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageBlurRepeatUnclippedGM", comparison.similarity)
        assertTrue(accepted, "ImageBlurRepeatUnclippedGM regressed below ratchet")
    }
}
