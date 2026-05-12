package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug9819Test {

    @Test
    fun `Skbug9819GM matches skbug_9819_png within tolerance`() {
        val gm = Skbug9819GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug_9819.png")

        // Phase G4b proof-of-concept GM. Tolerance kept loose (=4) — the
        // check-mark glyphs run through stroked-line AA paths that have
        // their own per-channel residual versus the upstream reference,
        // and the BGRA-vs-RGBA equivalence is asserted directly by
        // SkBitmapBgra8888Test. This GM exists to demonstrate the new
        // colour type survives the full canvas → bitmap → image →
        // shader → device round-trip without regressing 8888 / Alpha8.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Skbug9819GM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug9819GM", comparison.similarity)
        assertTrue(accepted, "Skbug9819GM regressed below ratchet")
    }
}
