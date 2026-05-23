package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawBitmapRectGMTest {

    @Test
    @Disabled("SLOW.GM_STRESS: ~1 min bitmap-rect variant in the default ratchet run; run this class explicitly for drawBitmapRect changes.")
    fun `DrawBitmapRect bitmap variant matches drawbitmaprect_png within tolerance`() {
        runVariant(DrawBitmapRectGM(DrawBitmapRectGM.Variant.BITMAP), "DrawBitmapRectGM_bitmap")
    }

    @Test
    @Disabled("SLOW.GM_STRESS: ~30s bitmap-subset variant; run this class explicitly for drawBitmapRect changes.")
    fun `DrawBitmapRect bitmap-subset variant matches drawbitmaprect-subset_png within tolerance`() {
        runVariant(DrawBitmapRectGM(DrawBitmapRectGM.Variant.BITMAP_SUBSET), "DrawBitmapRectGM_subset")
    }

    @Test
    @Disabled("SLOW.GM_STRESS: ~1 min image-rect variant in the default ratchet run; run this class explicitly for drawImageRect changes.")
    fun `DrawBitmapRect imagerect variant matches drawbitmaprect-imagerect_png within tolerance`() {
        runVariant(DrawBitmapRectGM(DrawBitmapRectGM.Variant.IMAGE), "DrawBitmapRectGM_imagerect")
    }

    @Test
    @Disabled("SLOW.GM_STRESS: ~15s image-rect subset variant; run this class explicitly for drawImageRect changes.")
    fun `DrawBitmapRect imagerect-subset variant matches drawbitmaprect-imagerect-subset_png within tolerance`() {
        runVariant(
            DrawBitmapRectGM(DrawBitmapRectGM.Variant.IMAGE_SUBSET),
            "DrawBitmapRectGM_imagerect_subset",
        )
    }

    private fun runVariant(gm: DrawBitmapRectGM, label: String) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(label, comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(label, comparison.similarity)
        assertTrue(accepted, "$label regressed below ratchet")
    }
}
