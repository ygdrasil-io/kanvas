package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class EncodeTest {

    @Test
    fun `EncodeGM matches encode_png within tolerance`() {
        val gm = EncodeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode.png")
        // JPEG re-encode loses a small amount of precision relative to
        // upstream's libjpeg-turbo encode, so we use the textual tolerance
        // value (=8) rather than the photographic floor.
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("EncodeGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EncodeGM", comparison.similarity)
        assertTrue(accepted, "EncodeGM regressed below ratchet")
    }
}
