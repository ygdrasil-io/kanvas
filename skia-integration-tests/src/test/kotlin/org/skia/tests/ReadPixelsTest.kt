package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
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
 * | [ReadPixelsGM]        | STUB.FIXTURE.GOOGLE_CHROME_ICO | absent | needs `images/google_chrome.ico` + an ICO codec |
 * | [ReadPixelsCodecGM]   | STUB.FIXTURE.READPIXELSCODEC_REFERENCE | source ok | reference PNG `original-888/readpixelscodec.png` absent |
 * | [ReadPixelsPictureGM] | STUB.FIXTURE.READPIXELSPICTURE_REFERENCE | n/a | reference PNG `original-888/readpixelspicture.png` absent |
 */
class ReadPixelsTest {

    @Test
    @Disabled(
        "STUB.FIXTURE.GOOGLE_CHROME_ICO: ReadPixelsGM upstream loads " +
            "`images/google_chrome.ico` via SkCodec::MakeFromStream, then " +
            "resamples to 64×64 in three source colour types. The ICO fixture " +
            "is not shipped under `kanvas-legacy/src/test/resources/images/`, " +
            "and the ICO decoder is itself a STUB.ICO_DECODE in :cpu-raster. " +
            "Body is fully ported against the live SkImage.readPixels surface " +
            "(see ReadPixelsHelpers.drawImage) — drop this @Disabled once the " +
            "fixture and the ICO decoder both land.",
    )
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
    @Disabled(
        "STUB.FIXTURE.READPIXELSCODEC_REFERENCE: ReadPixelsCodecGM body is " +
            "fully ported against the live SkImage.readPixels / SkImageCodecs " +
            ".DeferredFromEncodedData surface (uses `images/randPixels.png`, " +
            "which ships under kanvas-legacy/src/test/resources/images/), but " +
            "the upstream reference PNG `original-888/readpixelscodec.png` is " +
            "not present in the project. Drop this @Disabled once the reference " +
            "lands and the cross-backend ratchet accepts the rendered pixels.",
    )
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
    @Disabled(
        "STUB.FIXTURE.READPIXELSPICTURE_REFERENCE: ReadPixelsPictureGM body is " +
            "fully ported against the live SkImage.readPixels / " +
            "SkImages.DeferredFromPicture surface (no source fixture needed — " +
            "the picture is recorded in-memory), but the upstream reference PNG " +
            "`original-888/readpixelspicture.png` is not present in the project. " +
            "Drop this @Disabled once the reference lands and the cross-backend " +
            "ratchet accepts the rendered pixels.",
    )
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
