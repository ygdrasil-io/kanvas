package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.codec.SkEncodedImageFormat
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class EncodeColorTypesTest {

    @Test
    fun `EncodeColorTypesGM webp-lossless matches encode-color-types-webp-lossless within tolerance`() {
        val gm = EncodeColorTypesGM(
            SkEncodedImageFormat.kWEBP, 100,
            EncodeColorTypesGM.Variant.kNormal, "webp-lossless",
        )
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode-color-types-webp-lossless.png")
        // Lossless WEBP round-trip preserves pixels exactly. Tolerance
        // is small ; floor stays modest because our SkBitmap doesn't
        // tag a per-bitmap alpha-type so the premul / unpremul cells
        // render the same source pixels (visually equivalent to the
        // reference whenever encode preserves alpha).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("EncodeColorTypesWebpLosslessGM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(
            "EncodeColorTypesWebpLosslessGM", comparison.similarity,
        )
        assertTrue(accepted, "EncodeColorTypesWebpLosslessGM regressed below ratchet")
    }
}
