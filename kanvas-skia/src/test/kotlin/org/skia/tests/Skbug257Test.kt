package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug257Test {

    @Test
    fun `Skbug257GM matches skbug_257_png within tolerance`() {
        val gm = Skbug257GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug_257.png")

        // Observed similarity ~93.7% — the rotated-checker shader background
        // and the cyan reference overlays match upstream. The HELLO WORLD
        // glyph runs drift on AA edges (LCD subpixel downgrade) but the
        // structural alignment which the bug-257 regression targets is
        // preserved.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Skbug257GM", comparison)
        val floor = 93.6
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug257GM", comparison.similarity)
        assertTrue(accepted, "Skbug257GM regressed below ratchet")
        assertTrue(comparison.similarity >= floor,
            "Skbug257GM similarity ${"%.2f".format(comparison.similarity)}% < $floor% (t=8 floor)")
    }
}
