package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawBitmapRectSkbug4734Test {

    @Test
    fun `DrawBitmapRectSkbug4734GM matches draw_bitmap_rect_skbug4734_png within tolerance`() {
        val gm = DrawBitmapRectSkbug4734GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image draw_bitmap_rect_skbug4734.png")
        // Single `drawImageRect` with sub-pixel `src` inset and 8× upscale.
        // Output is a 56×40 nearest-neighbour bilinear-free patch ; pixels
        // match upstream cleanly except for edge sampling under fractional
        // `src` which depends on Skia's sampler-with-inset rounding.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawBitmapRectSkbug4734GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawBitmapRectSkbug4734GM", comparison.similarity)
        assertTrue(accepted, "DrawBitmapRectSkbug4734GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.5,
            "DrawBitmapRectSkbug4734GM similarity ${"%.2f".format(comparison.similarity)}% < 99.5% floor",
        )
    }
}
