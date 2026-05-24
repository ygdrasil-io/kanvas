package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurRectGalleryTest {

    @Test
    fun `BlurRectGalleryGM matches reference`() {
        val gm = BlurRectGalleryGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blurrect_gallery.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurRectGalleryGM", comparison)
        if (comparison.similarity < 55.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurRectGalleryGM", comparison.similarity)
        assertTrue(accepted, "BlurRectGalleryGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 55.0,
            "BlurRectGalleryGM similarity ${"%.2f".format(comparison.similarity)}% < 55.0% floor",
        )
    }
}
