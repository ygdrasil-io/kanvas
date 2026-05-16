package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BitmapPremulTest {

    @Test
    fun `BitmapPremulGM matches bitmap_premul_png within tolerance`() {
        val gm = BitmapPremulGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bitmap_premul.png")

        // Phase G4c proof-of-concept GM. Upstream's reference is meant to
        // be uniformly white — any drift away from white reveals premul /
        // unpremul bugs. Tolerance kept loose (=2) because our 4444
        // path does not dither (upstream's does) and the per-row alpha
        // gradient + 4-bit quantisation interact at the sub-byte level.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 2)
        TestReport.recordDetailed("BitmapPremulGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BitmapPremulGM", comparison.similarity)
        assertTrue(accepted, "BitmapPremulGM regressed below ratchet")
    }
}
