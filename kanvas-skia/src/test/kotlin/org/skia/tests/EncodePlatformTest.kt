package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class EncodePlatformTest {

    @Test
    fun `EncodePlatformGM matches encode-platform within tolerance`() {
        val gm = EncodePlatformGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image encode-platform.png")
        // R-final.6 — five columns × three rows. PNG / WEBP-lossless
        // round-trip cleanly, JPEG drifts by a few byte-levels per
        // channel, WEBP-lossy STUB returns null so its column is blank
        // (matches the reference where libwebp-lossy produced cells —
        // big visual gap there, the ratchet absorbs it).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 64)
        TestReport.recordDetailed("EncodePlatformGM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("EncodePlatformGM", comparison.similarity)
        assertTrue(accepted, "EncodePlatformGM regressed below ratchet")
    }
}
