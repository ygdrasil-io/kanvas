package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase GM-port runner for [BitmapImageGM] (registered as
 * `bitmap-image-srgb-legacy`).
 *
 * The upstream test compares sRGB-tagged and untagged ("legacy") JPEG
 * decodes. `:kanvas-skia` always tags surfaces with sRGB, so the
 * legacy / sRGB rows render identically — small drift vs upstream's
 * legacy row is expected (untagged path skips gamut clipping). Floor
 * is intentionally loose.
 */
class BitmapImageTest {

    @Test
    fun `BitmapImageGM matches bitmap_image_srgb_legacy_png within tolerance`() {
        val gm = BitmapImageGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bitmap-image-srgb-legacy.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("BitmapImageGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BitmapImageGM", comparison.similarity)
        assertTrue(accepted, "BitmapImageGM regressed below ratchet")
    }
}
