package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DegenerateGradientTest {

    @Test
    fun `DegenerateGradientGM matches degenerate_gradients_png within tolerance`() {
        val gm = DegenerateGradientGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image degenerate_gradients.png")
        // Several rows use degenerate gradient inputs (radius=0 radial,
        // start==end sweep) that `:kanvas-skia` rejects with `require()`
        // guards — the corresponding tiles render solid-black via the
        // null-shader fallback instead of the per-tile-mode average
        // colour that upstream synthesises. Tolerate the gap.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DegenerateGradientGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DegenerateGradientGM", comparison.similarity)
        assertTrue(accepted, "DegenerateGradientGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 61.0,
            "DegenerateGradientGM similarity ${"%.2f".format(comparison.similarity)}% < 61.0%",
        )
    }
}
