package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase H1-finish-B runner for [AllBitmapConfigsGM] (`all_bitmap_configs`).
 *
 * Six-row mosaic stacking the same `color_wheel.png` decoded into
 * different `SkColorType`s. `kRGB_565` and `kGray_8` are absent from
 * `:kanvas-skia`, so the corresponding rows render as the underlying
 * checker — similarity is loose-floor.
 */
class AllBitmapConfigsTest {

    @Test
    fun `AllBitmapConfigsGM matches all_bitmap_configs_png within tolerance`() {
        val gm = AllBitmapConfigsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image all_bitmap_configs.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("AllBitmapConfigsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AllBitmapConfigsGM", comparison.similarity)
        assertTrue(accepted, "AllBitmapConfigsGM regressed below ratchet")
    }
}
