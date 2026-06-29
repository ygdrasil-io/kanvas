package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for the R-final.5 [AnimatedGifGM] — exercises
 * [org.graphiks.kanvas.codec.SkCodec.getFrameCount] +
 * [org.graphiks.kanvas.codec.SkCodec.getFrameInfo] +
 * [org.graphiks.kanvas.codec.SkCodec.Options]`(frameIndex, priorFrame)`.
 */
class AnimatedGifTest {

    @Test
    fun `AnimatedGifGM matches animatedGif_png within tolerance`() {
        val gm = AnimatedGifGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("AnimatedGifGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnimatedGifGM", comparison.similarity)
        assertTrue(accepted, "AnimatedGifGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 1.0,
            "AnimatedGifGM similarity ${"%.2f".format(comparison.similarity)}% < 1% floor",
        )
    }
}
