package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class FillrectGradientTest {
    @Test
    fun `FillrectGradientGM matches fillrect_gradient_png within tolerance`() {
        val gm = FillrectGradientGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image fillrect_gradient.png")
        // 9 rows × 2 cols of gradient cells (linear / radial). Stresses
        // every stop-list permutation: 2/3 stops, sub-range, single-stop,
        // duplicate-position, unsorted. First end-to-end gradient GM.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("FillrectGradientGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("FillrectGradientGM", comparison.similarity)
        assertTrue(accepted, "FillrectGradientGM regressed below ratchet")
        assertTrue(comparison.similarity >= 60.0,
            "FillrectGradientGM similarity ${"%.2f".format(comparison.similarity)}% < 60.0% (8-bit lerp vs upstream F16; row-9 unsorted-stop drift)")
    }
}
