package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class NearestHalfPixelImageTest {

    @Test
    fun `NearestHalfPixelImageGM matches nearest_half_pixel_image_png within tolerance`() {
        val gm = NearestHalfPixelImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image nearest_half_pixel_image.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("NearestHalfPixelImageGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("NearestHalfPixelImageGM", comparison.similarity)
        assertTrue(accepted, "NearestHalfPixelImageGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 5.0,
            "NearestHalfPixelImageGM similarity ${"%.2f".format(comparison.similarity)}% < 5.0% floor",
        )
    }
}
