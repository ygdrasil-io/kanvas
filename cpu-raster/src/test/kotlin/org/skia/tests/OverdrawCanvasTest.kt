package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class OverdrawCanvasTest {
    @Test
    fun `OverdrawCanvasGM matches overdraw_canvas_png within tolerance`() {
        val gm = OverdrawCanvasGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image overdraw_canvas.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("OverdrawCanvasGM", comparison)
        if (comparison.similarity < THRESHOLD) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("OverdrawCanvasGM", comparison.similarity)
        assertTrue(accepted, "OverdrawCanvasGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= THRESHOLD,
            "OverdrawCanvasGM similarity ${"%.2f".format(comparison.similarity)}% < $THRESHOLD%",
        )
    }

    private companion object {
        // overdraw_canvas overlays a heat-map filter on a counter-bitmap +
        // a regular-font label. Glyph-edge AA between rasterisers gives a
        // small drift on the label, but the heatmap heatmap interior matches.
        private const val THRESHOLD: Double = 85.3
    }
}
