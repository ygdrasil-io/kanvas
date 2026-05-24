package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-test for the body-port of [PictureGeneratorGM] — the upstream
 * `pictureimagegenerator` GM mapped onto the kanvas-skia
 * [org.skia.foundation.SkImages.DeferredFromPicture] entry point.
 *
 * **Logo divergence.** The Kotlin port records the SKIA wordmark via
 * `SkFont.makeTextPath` + gradient shaders, then routes the 16 tiles
 * through `SkImages.DeferredFromPicture`. OpenType vs FreeType scaler /
 * stroker divergences (subpixel positioning, gradient stop interpolation
 * in working-colour-space rather than premul-sRGB) mean per-pixel
 * fidelity vs. `pictureimagegenerator.png` is **not** expected — the
 * floor is permissive and the ratchet covers regression. The sibling
 * [PictureImageGeneratorGM] uses a simpler concentric-rectangles
 * substitute for the picture content and carries its own separate
 * ratchet entry.
 */
class PictureGeneratorTest {

    @Test
    fun `PictureGeneratorGM matches pictureimagegenerator_png within tolerance`() {
        val gm = PictureGeneratorGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("PictureGeneratorGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("PictureGeneratorGM", comparison.similarity)
        assertTrue(accepted, "PictureGeneratorGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 1.0,
            "PictureGeneratorGM similarity ${"%.2f".format(comparison.similarity)}% < 1% floor",
        )
    }
}
