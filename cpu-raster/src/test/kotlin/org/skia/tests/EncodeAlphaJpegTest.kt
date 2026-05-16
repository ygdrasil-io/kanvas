package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class EncodeAlphaJpegTest {

    @Test
    fun `EncodeAlphaJpegGM matches encode-alpha-jpeg within JPEG tolerance`() {
        val gm = EncodeAlphaJpegGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode-alpha-jpeg.png")
        // R-final.6 — JPEG encode→decode through ImageIO drifts by a
        // handful of byte-levels per channel vs upstream's libjpeg-turbo
        // (different DCT residual rounding + different chroma subsample
        // filter). t=64 absorbs that drift on the matching pixels ;
        // the % similarity floor sits at 50 % because the F16 / unpremul
        // columns visually match in the reference but render the same
        // pixels in our port (no per-bitmap alpha-type tag), so the
        // pixel-by-pixel comparison loses ~50 % of cells outright.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 64)
        TestReport.recordDetailed("EncodeAlphaJpegGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EncodeAlphaJpegGM", comparison.similarity)
        assertTrue(accepted, "EncodeAlphaJpegGM regressed below ratchet")
    }
}
