package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClipDrawDrawTest {

    @Test
    fun `ClipDrawDrawGM matches clipdrawdraw_png within tolerance`() {
        val gm = ClipDrawDrawGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clipdrawdraw.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ClipDrawDrawGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClipDrawDrawGM", comparison.similarity)
        assertTrue(accepted, "ClipDrawDrawGM regressed below tolerance")
        // Floor 99 % — Phase 6i edge-rounding fix made `clipRect` and
        // non-AA `drawRect` agree on round-half-up, eliminating 1-px
        // remnants. Phase 6s `eraseColor` colorspace xform then closed
        // the residual BG drift (≤ 6-byte offset on `0xCCCCCC` light
        // grey through sRGB → Rec.2020), lifting this GM from 35.4 %
        // → 100.0 %.
        assertTrue(
            comparison.similarity >= 99.0,
            "ClipDrawDrawGM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% floor",
        )
    }
}
