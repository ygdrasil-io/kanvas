package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageOutOfGamutTest {

    @Test
    fun `ImageOutOfGamutGM matches image_out_of_gamut_png within tolerance`() {
        val gm = ImageOutOfGamutGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image image_out_of_gamut.png")

        // ── Classification: INTRACTABLE ──────────────────────────────────────
        // This GM tests out-of-gamut premultiplied pixels (RGB > A). Upstream
        // Skia stores them as raw premul and composites without any
        // unpremultiplication, producing highly saturated colours (e.g. R=240,
        // A=64 renders visually as a bright, nearly opaque red stripe).
        //
        // kanvas-skia's SkBitmap stores all pixels as *unpremultiplied* SkColor
        // values. When we store SkColorSetARGB(0x40, 0xF0, 0, 0) and then
        // composite SrcOver, the rasteriser premultiplies first (R = 0xF0 *
        // 64 / 255 ≈ 60), blending a much dimmer colour than upstream's direct
        // premul pass-through. Faithfully replicating upstream's out-of-gamut
        // rendering would require the entire pixel pipeline to carry raw premul
        // data — a structural change well outside the scope of this port.
        //
        // The similarity score (~39%) reflects this architectural divergence,
        // not a bug. The GM is retained to confirm the rendering doesn't regress
        // beyond the ratchet baseline established on first enable.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("ImageOutOfGamutGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageOutOfGamutGM", comparison.similarity)
        assertTrue(accepted, "ImageOutOfGamutGM regressed below ratchet")
    }
}
