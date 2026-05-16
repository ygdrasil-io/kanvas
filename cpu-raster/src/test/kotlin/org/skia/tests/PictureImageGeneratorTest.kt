package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for the R-final.5 [PictureImageGeneratorGM].
 *
 * **Picture-content divergence.** The Kotlin port substitutes a
 * concentric-rectangles vector pattern for the upstream SKIA wordmark
 * (which depends on `SkTextUtils::GetPath` + multi-stop linear
 * gradients we don't fully wire up at the
 * [org.skia.foundation.SkImages.DeferredFromPicture] layer). Pixel
 * fidelity vs. `pictureimagegenerator.png` is **not** expected — the
 * floor is permissive and the ratchet covers regression.
 */
class PictureImageGeneratorTest {

    @Test
    fun `PictureImageGeneratorGM matches pictureimagegenerator_png within tolerance`() {
        val gm = PictureImageGeneratorGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("PictureImageGeneratorGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PictureImageGeneratorGM", comparison.similarity)
        assertTrue(accepted, "PictureImageGeneratorGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 1.0,
            "PictureImageGeneratorGM similarity ${"%.2f".format(comparison.similarity)}% < 1% floor",
        )
    }
}
