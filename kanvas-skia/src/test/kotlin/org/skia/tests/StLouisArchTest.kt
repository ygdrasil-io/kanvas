package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class StLouisArchTest {

    @Test
    fun `StLouisArchGM matches stlouisarch_png within tolerance`() {
        val gm = StLouisArchGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image stlouisarch.png")
        // Six hairline-stroked paths under scale(1, -1) + translate.
        // Three curve types (quad, cubic, conic) plus a degenerate flat
        // variant of each. The hairline path currently falls back to
        // strokeWidth=1 (true hairline scan-line is a follow-up phase),
        // so coverage broadens by one row of pixels per stroke vs upstream.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("StLouisArchGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("StLouisArchGM", comparison.similarity)
        assertTrue(accepted, "StLouisArchGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "StLouisArchGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% (hairline fallback)")
    }
}
