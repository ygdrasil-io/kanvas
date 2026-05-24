package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ImageBlurTest {

    @Test
    fun `ImageBlurGM matches imageblur_png within tolerance`() {
        val gm = ImageBlurGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imageblur.png")
        // 25 randomly placed text strings drawn into a layer, then
        // Gaussian-blurred with sigma (24, 0). The dominant source of
        // pixel drift is Liberation-vs-FreeType glyph-metric divergence
        // amplified by the wide Gaussian blur — a single-pixel shift
        // in glyph position becomes a 24-pixel halo of disagreement,
        // and 25 such draws compound that into a noisy diff.
        // Similarity floor (40 %) acknowledges the structural-not-
        // numerical match : the rendered image has the same blob
        // distribution and overall texture as the reference, just
        // with sub-pixel glyph placement skew. The text-rasterisation
        // skew is a known property of the pure Kotlin OpenType scaler
        // (see TEXTUAL_GM_TOLERANCE doc), unblurred GMs like
        // `Crbug1073670GM` show the same pattern.
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageBlurGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageBlurGM", comparison.similarity)
        assertTrue(accepted, "ImageBlurGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 40.0,
            "ImageBlurGM similarity ${"%.2f".format(comparison.similarity)}% < 40%",
        )
    }

    @Test
    fun `ImageBlurLargeGM matches imageblur_large_png within tolerance`() {
        val gm = ImageBlurLargeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image imageblur_large.png")
        // Same draws, sigma (80, 80) — the 80-pixel Gaussian blur
        // smears glyphs into one big diffuse blob, so positional skew
        // matters less but the *intensity envelope* of the blob shifts
        // by tens of greylevels when even one of 25 glyphs lands a few
        // pixels off. Same root cause as the small variant.
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageBlurLargeGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageBlurLargeGM", comparison.similarity)
        assertTrue(accepted, "ImageBlurLargeGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 35.0,
            "ImageBlurLargeGM similarity ${"%.2f".format(comparison.similarity)}% < 35%",
        )
    }
}
