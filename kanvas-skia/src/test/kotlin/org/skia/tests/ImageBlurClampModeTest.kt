package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageBlurClampModeTest {

    @Test
    fun `ImageBlurClampModeGM matches imageblurclampmode_png within tolerance`() {
        val gm = ImageBlurClampModeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imageblurclampmode.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ImageBlurClampModeGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageBlurClampModeGM", comparison.similarity)
        assertTrue(accepted, "ImageBlurClampModeGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 87.5,
            "ImageBlurClampModeGM similarity ${"%.2f".format(comparison.similarity)}% < 87.5%",
        )
    }
}
