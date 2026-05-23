package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [AnimatedTiledImageBlurGM].
 *
 * Snapshots the t=0 frame (blurSigma = 75f) of upstream's
 * `animated-tiled-image-blur` GM : a 2×2 grid of 250×250 blur
 * crops of `mandrill_512.png` using the four [org.skia.foundation.SkTileMode]
 * values (kDecal / kClamp / kRepeat / kMirror).
 *
 * Tolerance is set moderately high because the blur kernel sigma (75)
 * approaches the image edge at the crop boundary, making
 * tile-mode-dependent edge pixels the main source of diff.
 */
class AnimatedTiledImageBlurTest {

    @Test
    fun `AnimatedTiledImageBlurGM matches animated-tiled-image-blur_png within tolerance`() {
        val gm = AnimatedTiledImageBlurGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image animated-tiled-image-blur.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AnimatedTiledImageBlurGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnimatedTiledImageBlurGM", comparison.similarity)
        assertTrue(accepted, "AnimatedTiledImageBlurGM regressed below ratchet")
    }
}
