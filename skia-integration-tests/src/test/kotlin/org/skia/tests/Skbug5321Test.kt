package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug5321Test {

    @Test
    fun `Skbug5321GM matches skbug_5321_png within tolerance`() {
        val gm = Skbug5321GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug_5321.png")

        // Tiny canvas (128x128), two short text strings with kAlias edging
        // ; the background is white and the glyph ink is small. Floor is
        // set conservatively — combining-mark handling depends on the
        // backing typeface's cmap (the portable OpenType backend may emit
        // `.notdef` for U+0300 if the selected face lacks it).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Skbug5321GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug5321GM", comparison.similarity)
        assertTrue(accepted, "Skbug5321GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 95.0,
            "Skbug5321GM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
