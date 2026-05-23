package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Runner for [NotNative32BitmapConfigGM] (`not_native32_bitmap_config`).
 *
 * 128 × 128 colour-wheel rendered into a `kRGBA_8888` raster surface
 * then copied into a `kBGRA_8888` bitmap and re-blitted over a checker
 * background. Both 8888 colour types share the Pascal-Argb backing in
 * `:kanvas-skia`, so the visual output is colour-equivalent — used
 * to confirm that the non-native colour-type path round-trips cleanly.
 */
class NotNative32BitmapConfigTest {

    @Test
    fun `NotNative32BitmapConfigGM matches not_native32_bitmap_config_png within tolerance`() {
        val gm = NotNative32BitmapConfigGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image not_native32_bitmap_config.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("NotNative32BitmapConfigGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("NotNative32BitmapConfigGM", comparison.similarity)
        assertTrue(accepted, "NotNative32BitmapConfigGM regressed below ratchet")
    }
}
