package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkEncodedImageFormat
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class EncodeSrgbTest {

    @Test
    fun `EncodeSrgbGM PNG matches encode-srgb-png within tolerance`() {
        val gm = EncodeSrgbGM(SkEncodedImageFormat.kPNG)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode-srgb-png.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 32)
        TestReport.recordDetailed("EncodeSrgbPngGM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EncodeSrgbPngGM", comparison.similarity)
        assertTrue(accepted, "EncodeSrgbPngGM regressed below ratchet")
    }

    @Test
    fun `EncodeSrgbGM JPG matches encode-srgb-jpg within tolerance`() {
        val gm = EncodeSrgbGM(SkEncodedImageFormat.kJPEG)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode-srgb-jpg.png")
        // JPEG quantisation drifts more than PNG ; tolerance is widened.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 64)
        TestReport.recordDetailed("EncodeSrgbJpgGM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EncodeSrgbJpgGM", comparison.similarity)
        assertTrue(accepted, "EncodeSrgbJpgGM regressed below ratchet")
    }

    @Test
    fun `EncodeSrgbGM WEBP matches encode-srgb-webp within tolerance`() {
        val gm = EncodeSrgbGM(SkEncodedImageFormat.kWEBP)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode-srgb-webp.png")
        // VP8L is lossless, so pixel-identical to PNG tolerance is reasonable.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 32)
        TestReport.recordDetailed("EncodeSrgbWebpGM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EncodeSrgbWebpGM", comparison.similarity)
        assertTrue(accepted, "EncodeSrgbWebpGM regressed below ratchet")
    }
}
