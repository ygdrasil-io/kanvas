package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [AnimatedBackdropBlurGM].
 *
 * Snapshots the t=0 frame (vOffset = 0) of upstream's
 * `animated-backdrop-blur` GM : Lorem-ipsum text + color_wheel thumbnail,
 * with a Crop+Blur+Crop backdrop filter applied via [org.skia.core.SaveLayerRec].
 *
 * Tolerance is moderate — the backdrop blur produces soft edges and
 * the text rendering uses AWT (vs FreeType upstream), so pixel-level
 * deltas on glyph outlines are expected. The overall scene structure
 * (blurred band at 100..400, clear text above/below) should match.
 */
class AnimatedBackdropBlurTest {

    @Test
    fun `AnimatedBackdropBlurGM matches animated-backdrop-blur_png within tolerance`() {
        val gm = AnimatedBackdropBlurGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image animated-backdrop-blur.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AnimatedBackdropBlurGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnimatedBackdropBlurGM", comparison.similarity)
        assertTrue(accepted, "AnimatedBackdropBlurGM regressed below ratchet")
    }
}
