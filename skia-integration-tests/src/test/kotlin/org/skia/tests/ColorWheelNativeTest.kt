package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Second textual GM port — see [ColorWheelNativeGM] for the source-spec
 * mapping. Smaller (128 × 28) and more diverse than [BigTextGM]:
 * exercises non-AA text (`SkFont.Edging.kAlias`), 7 distinct colours,
 * Liberation Sans **Bold** (vs `BigTextGM`'s Regular), and the new
 * [org.skia.tools.ToolUtils.CreatePortableTypeface] family lookup.
 *
 * Tolerance 8 + floor 95% match the convention established by
 * [BigTextTest].
 */
class ColorWheelNativeTest {

    @Test
    fun `ColorWheelNativeGM matches colorwheelnative_png within tolerance`() {
        val gm = ColorWheelNativeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image colorwheelnative.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ColorWheelNativeGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ColorWheelNativeGM", comparison.similarity)
        assertTrue(accepted, "ColorWheelNativeGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 95.0,
            "ColorWheelNativeGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
