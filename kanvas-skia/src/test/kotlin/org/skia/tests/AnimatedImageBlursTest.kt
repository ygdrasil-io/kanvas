package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase H3 wave 11B runner for [AnimatedImageBlursGM].
 *
 * Snapshots the t=0 frame of upstream's animation : 30 procedurally
 * placed rounded rectangles, each drawn through a per-node Gaussian
 * blur saveLayer. Tolerance stays moderate ; sigma sequencing lines
 * up bit-for-bit with upstream via [org.skia.tools.SkRandom], so the
 * scene structure (counts / positions / sigmas) is identical and only
 * the per-pixel Gaussian discretisation differs.
 */
class AnimatedImageBlursTest {

    @Test
    fun `AnimatedImageBlursGM matches animated-image-blurs_png within tolerance`() {
        val gm = AnimatedImageBlursGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image animated-image-blurs.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("AnimatedImageBlursGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AnimatedImageBlursGM", comparison.similarity)
        assertTrue(accepted, "AnimatedImageBlursGM regressed below ratchet")
    }
}
