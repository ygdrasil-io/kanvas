package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class OverdrawColorFilterTest {

    @Test
    fun `OverdrawColorFilterGM matches overdrawcolorfilter_png within tolerance`() {
        val gm = OverdrawColorFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image overdrawcolorfilter.png")

        // Tolerance kept loose — the colour filter operates on the alpha
        // byte sampled from the kAlpha_8 source, but the destination
        // compositing still routes through our Rec.2020 working space
        // (subtle per-pixel drift vs upstream's sRGB working space).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("OverdrawColorFilterGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("OverdrawColorFilterGM", comparison.similarity)
        assertTrue(accepted, "OverdrawColorFilterGM regressed below ratchet")
    }
}
