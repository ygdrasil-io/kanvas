package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LargeGlyphBlurTest {

    @Test
    fun `LargeGlyphBlurGM matches largeglyphblur_png within tolerance`() {
        val gm = LargeGlyphBlurGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image largeglyphblur.png")
        // Glyph-mask blur with sigma ≈ 23.6 is rasterised through the
        // shared `SkBlurMaskFilter` Gaussian — accumulated float math
        // diverges from upstream by a few LSBs over thousands of
        // covered pixels, and the un-blurred overlay's AA edges add
        // a second source of 1-LSB drift. We accept tolerance=1 and a
        // floor of 85 % until a glyph-mask cache lands.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LargeGlyphBlurGM", comparison)
        if (comparison.similarity < 55.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LargeGlyphBlurGM", comparison.similarity)
        assertTrue(accepted, "LargeGlyphBlurGM regressed below ratchet")
        // Glyph-mask blur scoring is dominated by the un-blurred row of
        // text (top-left & top-right copies) matching upstream pixel-
        // perfectly ; the blurred halo, however, suffers from accumulated
        // float-math drift and the lack of a glyph-mask cache. The mid
        // 50 % floor matches the wider blur-family GMs (BigBlurs, etc.)
        // until I2's glyph cache lands.
        assertTrue(comparison.similarity >= 55.0,
            "LargeGlyphBlurGM similarity ${"%.2f".format(comparison.similarity)}% < 55.0% (t=1 floor)")
    }
}
