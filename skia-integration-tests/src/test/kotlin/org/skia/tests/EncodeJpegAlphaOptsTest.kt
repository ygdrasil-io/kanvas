package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test for [EncodeJpegAlphaOptsGM] — companion to [EncodeAlphaJpegTest].
 *
 * Both classes target the same upstream GM (`gm/encode_alpha_jpeg.cpp::
 * EncodeJpegAlphaOptsGM`), but [EncodeJpegAlphaOptsGM] preserves the
 * upstream 8888 ↔ F16 source-pixmap split — the F16 columns route
 * through a [SkColorType.kRGBA_F16Norm] intermediate before the
 * encoder reads the pixels back as non-premultiplied ARGB. Encoding
 * tolerance is widened to 64 to absorb the JPEG-quantizer drift
 * between our ImageIO encode and upstream's libjpeg-turbo reference.
 */
class EncodeJpegAlphaOptsTest {

    @Test
    fun `EncodeJpegAlphaOptsGM matches encode-alpha-jpeg within JPEG tolerance`() {
        val gm = EncodeJpegAlphaOptsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode-alpha-jpeg.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 64)
        TestReport.recordDetailed("EncodeJpegAlphaOptsGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EncodeJpegAlphaOptsGM", comparison.similarity)
        assertTrue(accepted, "EncodeJpegAlphaOptsGM regressed below ratchet")
    }
}
