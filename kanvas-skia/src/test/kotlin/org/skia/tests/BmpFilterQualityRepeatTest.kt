package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BmpFilterQualityRepeatTest {

    @Test
    fun `BmpFilterQualityRepeatGM matches bmp_filter_quality_repeat_png within tolerance`() {
        val gm = BmpFilterQualityRepeatGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bmp_filter_quality_repeat.png")
        // Four 200×150 panels of a 40×40 four-colour board tiled with
        // kRepeat under four sampling presets. The "high" (Mitchell)
        // and "medium" (linear+mipmap) panels exercise paths that don't
        // bit-match upstream Skia's GPU sampler — kanvas-skia's raster
        // bicubic vs. upstream's GPU sampler differ at sub-pixel edges,
        // and mipmaps aren't generated for the shader path so "medium"
        // collapses to plain linear. Tolerance 32 absorbs the resulting
        // per-channel drift while still asserting the four panels paint
        // the right palette at the right locations.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 32)
        TestReport.recordDetailed("BmpFilterQualityRepeatGM", comparison)
        if (comparison.similarity < 75.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BmpFilterQualityRepeatGM", comparison.similarity)
        assertTrue(accepted, "BmpFilterQualityRepeatGM regressed below ratchet")
        assertTrue(comparison.similarity >= 75.0,
            "BmpFilterQualityRepeatGM similarity ${"%.2f".format(comparison.similarity)}% < 75.0% (t=32 floor)")
    }
}
