package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StrokeRectRRectsTest {

    @Test
    fun `StrokeRectRRectsGM matches stroke_rect_rrects png within tolerance`() {
        val gm = StrokeRectRRectsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image stroke_rect_rrects.png")
        // A grid of stroked/filled rects and rrects at 6 stroke widths × 3 join
        // types, plus football and D-shaped rrect sections. Tests that
        // AnalyticRoundRectRenderStep-compatible geometries render correctly
        // with our CPU-raster stroker.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("StrokeRectRRectsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StrokeRectRRectsGM", comparison.similarity)
        assertTrue(accepted, "StrokeRectRRectsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "StrokeRectRRectsGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0%")
    }
}
