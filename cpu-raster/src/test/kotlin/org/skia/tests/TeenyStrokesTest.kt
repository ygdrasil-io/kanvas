package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TeenyStrokesTest {

    @Test
    fun `TeenyStrokesGM matches teenyStrokes_png within tolerance`() {
        val gm = TeenyStrokesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image teenyStrokes.png")
        // Five line-pairs drawn under CTM scales spanning 5 orders of magnitude
        // (1/2e-6 = 500000× down to 1/5e-5 = 20000×). Each pair *should* land
        // at the same device-space coordinates with the same 5-px stroke; only
        // single-precision float drift (and the stroker's user-space normal
        // arithmetic at sub-1e-5 magnitudes) can pull them apart. Tolerance=1
        // matches the colour-space pipeline's 1-ULP guarantee.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TeenyStrokesGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TeenyStrokesGM", comparison.similarity)
        assertTrue(accepted, "TeenyStrokesGM regressed below ratchet")
        assertTrue(comparison.similarity >= 95.0,
            "TeenyStrokesGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% (t=1 floor)")
    }
}
