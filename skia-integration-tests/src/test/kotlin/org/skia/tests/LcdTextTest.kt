package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LcdTextTest {

    @Test
    fun `LcdTextGM matches lcdtext_png within tolerance`() {
        val gm = LcdTextGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lcdtext.png")

        // Observed similarity ~90.2% with the portable OpenType backend :
        // four text rows render at the right
        // positions and sizes. The LCDRenderTrue rows downgrade silently
        // to plain antialiased coverage on the kanvas-skia SkFont (cf.
        // SkFont docstring), so glyph edge pixels drift slightly from
        // upstream's subpixel RGB-stripe coverage.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("LcdTextGM", comparison)
        val floor = 90.0
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LcdTextGM", comparison.similarity)
        assertTrue(accepted, "LcdTextGM regressed below ratchet")
        assertTrue(comparison.similarity >= floor,
            "LcdTextGM similarity ${"%.2f".format(comparison.similarity)}% < $floor% (t=8 floor)")
    }
}
