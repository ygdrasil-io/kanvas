package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for `textfilter_image` and `textfilter_color` GMs, ported from
 * `gm/imagefiltersbase.cpp` (`ImageFiltersText_IF` and `ImageFiltersText_CF`).
 *
 * Both GMs render a waterfall of "Hamburgefon" in three font edging modes
 * (alias, AA, subpixelAA) across four combinations of filter presence and
 * saveLayer usage. The reference PNGs use Skia's embedded test font; our
 * port uses AWT/Liberation so per-pixel similarity will be below 100%.
 * Floors are set conservatively to accommodate font metric differences.
 */
class ImageFiltersTextTest {

    @Test
    fun `ImageFiltersTextIfGM matches textfilter_image_png within tolerance`() {
        val gm = ImageFiltersTextIfGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image textfilter_image.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageFiltersTextIfGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersTextIfGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersTextIfGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "ImageFiltersTextIfGM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }

    @Test
    fun `ImageFiltersTextCfGM matches textfilter_color_png within tolerance`() {
        val gm = ImageFiltersTextCfGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image textfilter_color.png")
        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("ImageFiltersTextCfGM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ImageFiltersTextCfGM", comparison.similarity)
        assertTrue(accepted, "ImageFiltersTextCfGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "ImageFiltersTextCfGM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
