package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Test driver for [TypefaceStylingGM] (`typeface_styling.png`).
 *
 * The "embolden delta" row 3 will not match upstream pixel-for-pixel
 * (see GM KDoc) ; tolerance + floor are loose enough to admit that
 * deviation while still catching catastrophic regressions in the other
 * rows.
 */
class TypefaceStylingTest {

    @Test
    fun `TypefaceStylingGM matches typeface_styling_png within tolerance`() {
        val gm = TypefaceStylingGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image typeface_styling.png")

        val comparison = TestUtils.compareBitmapsDetailed(
            rendered, reference!!, tolerance = TestUtils.TEXTUAL_GM_TOLERANCE,
        )
        TestReport.recordDetailed("TypefaceStylingGM", comparison)
        if (comparison.similarity < 93.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TypefaceStylingGM", comparison.similarity)
        assertTrue(accepted, "TypefaceStylingGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 93.0,
            "TypefaceStylingGM similarity ${"%.2f".format(comparison.similarity)}% < 93.0% floor",
        )
    }
}
