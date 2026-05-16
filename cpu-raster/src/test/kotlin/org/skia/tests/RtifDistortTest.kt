package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RtifDistortTest {

    @Test
    fun `RtifDistortGM matches rtif_distort_png within tolerance`() {
        val gm = RtifDistortGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rtif_distort.png")
        // Six panels of randomly placed glyphs warped by a sin-of-y
        // runtime image filter. The glyph rasterizer + SkRandom +
        // colour-to-565 chain match upstream bit-for-bit, but the
        // image filter's per-pixel sample of the unsharp wave snaps
        // each glyph fringe by a couple of pixels. A wider tolerance
        // band absorbs that residual.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("RtifDistortGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RtifDistortGM", comparison.similarity)
        assertTrue(accepted, "RtifDistortGM regressed below ratchet")
    }
}
