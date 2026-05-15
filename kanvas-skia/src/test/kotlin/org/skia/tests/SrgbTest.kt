package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SrgbTest {

    @Test
    fun `SrgbGM matches srgb_colorfilter_png within tolerance`() {
        val gm = SrgbGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image srgb_colorfilter.png")
        // The GM draws mandrill_256.png six times under various
        // colour-filter combinations. The dominant residual versus the
        // reference is the AWT PNG decoder vs Skia's codec stack picking
        // slightly different colour-space conversion paths for the
        // mandrill image bytes, plus minor numeric drift in the
        // sRGB transfer LUTs (we evaluate the IEC 61966-2-1 curve
        // analytically per-pixel ; upstream pre-LUTs it). t=8 absorbs
        // that drift while still catching genuine filter regressions.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("SrgbGM", comparison)
        if (comparison.similarity < 30.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("SrgbGM", comparison.similarity)
        assertTrue(accepted, "SrgbGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "SrgbGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% (t=8 floor)",
        )
    }
}
