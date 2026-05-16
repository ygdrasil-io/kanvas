package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug12212Test {

    @Test
    fun `Skbug12212GM matches skbug_12212_png within tolerance`() {
        val gm = Skbug12212GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug_12212.png")

        // Reference renders with LCD subpixel antialiasing (kRGB_H_SkPixelGeometry)
        // — our pipeline downgrades to full-coverage AA, so glyph edges
        // carry a chromatic-fringe residual we can't match without an
        // LCD-AA path. Tolerance / floor accordingly loose.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Skbug12212GM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug12212GM", comparison.similarity)
        assertTrue(accepted, "Skbug12212GM regressed below ratchet")
    }
}
