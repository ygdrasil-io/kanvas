package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class OverdrawTextXformTest {

    @Test
    fun `OverdrawTextXformGM matches overdraw_text_xform_png within tolerance`() {
        val gm = OverdrawTextXformGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image overdraw_text_xform.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("OverdrawTextXformGM", comparison)
        if (comparison.similarity < THRESHOLD) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("OverdrawTextXformGM", comparison.similarity)
        assertTrue(accepted, "OverdrawTextXformGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= THRESHOLD,
            "OverdrawTextXformGM similarity ${"%.2f".format(comparison.similarity)}% < $THRESHOLD%",
        )
    }

    private companion object {
        // overdraw_text_xform compares two halves of a text-density heatmap.
        // Glyph-edge AA between rasterisers gives small drift on text edges;
        // the dense overdraw patterns in the interior match well.
        private const val THRESHOLD: Double = 85.0
    }
}
