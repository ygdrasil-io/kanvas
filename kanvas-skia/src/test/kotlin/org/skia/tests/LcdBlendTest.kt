package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LcdBlendTest {

    @Test
    fun `LcdBlendGM matches lcdblendmodes_png within tolerance`() {
        val gm = LcdBlendGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lcdblendmodes.png")

        // Observed similarity ~93.4% — four blend-mode columns render at
        // the right positions with the right background colours and 29
        // blend modes applied. Glyph edges drift on AA because the
        // upstream surface was configured with `kRGB_H_SkPixelGeometry`
        // (LCD RGB stripe), which kanvas-skia doesn't surface yet —
        // and the SkFont silently downgrades subpixel-AA edging to
        // plain AA. Structural content (mode names, blend pairings,
        // column backgrounds) is preserved.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("LcdBlendGM", comparison)
        val floor = 93.2
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LcdBlendGM", comparison.similarity)
        assertTrue(accepted, "LcdBlendGM regressed below ratchet")
        assertTrue(comparison.similarity >= floor,
            "LcdBlendGM similarity ${"%.2f".format(comparison.similarity)}% < $floor% (t=8 floor)")
    }
}
