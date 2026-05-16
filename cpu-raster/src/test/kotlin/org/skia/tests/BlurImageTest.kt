package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlurImageTest {

    @Test
    fun `BlurImageGM matches blur_image_png within tolerance`() {
        val gm = BlurImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blur_image.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlurImageGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlurImageGM", comparison.similarity)
        assertTrue(accepted, "BlurImageGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "BlurImageGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
