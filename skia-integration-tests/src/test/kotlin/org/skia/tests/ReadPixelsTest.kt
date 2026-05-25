package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Cross-backend ratchet drivers for the three GMs ported from
 * [`gm/readpixels.cpp`](https://github.com/google/skia/blob/main/gm/readpixels.cpp).
 *
 * The shared body lives in [ReadPixelsHelpers] ; per-GM bucket
 * classification :
 *
 * | GM | Bucket | Fixture | Notes |
 * |----|--------|---------|-------|
 * | [ReadPixelsGM]        | active | `images/google_chrome.ico` | fixture and codec are now available |
 * | [ReadPixelsCodecGM]   | active | `images/randPixels.png` + `original-888/readpixelscodec.png` | reference committed |
 * | [ReadPixelsPictureGM] | active | `original-888/readpixelspicture.png` | reference committed |
 */
class ReadPixelsTest {

    @Test
    fun `ReadPixelsGM matches readpixels_png within tolerance`() {
        val gm = ReadPixelsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image readpixels.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ReadPixelsGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ReadPixelsGM", comparison.similarity)
        assertTrue(accepted, "ReadPixelsGM regressed below ratchet")
    }

    @Test
    fun `ReadPixelsCodecGM matches readpixelscodec_png within tolerance`() {
        val gm = ReadPixelsCodecGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image readpixelscodec.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ReadPixelsCodecGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ReadPixelsCodecGM", comparison.similarity)
        assertTrue(accepted, "ReadPixelsCodecGM regressed below ratchet")
    }

    @Test
    fun `ReadPixelsPictureGM matches readpixelspicture_png within tolerance`() {
        val gm = ReadPixelsPictureGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image readpixelspicture.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ReadPixelsPictureGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ReadPixelsPictureGM", comparison.similarity)
        assertTrue(accepted, "ReadPixelsPictureGM regressed below ratchet")
    }
}
