package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug884166Test {

    @Test
    fun `Crbug884166GM matches crbug_884166_png within tolerance`() {
        val gm = Crbug884166GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_884166.png")
        // Single 8-vertex line-only contour, AA fill. The shallow sliver in
        // the path stresses winding-count + crossing detection; the residual
        // is sub-ulp coverage rounding on AA edges.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug884166GM", comparison)
        if (comparison.similarity < 98.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug884166GM", comparison.similarity)
        assertTrue(accepted, "Crbug884166GM regressed below ratchet")
        assertTrue(comparison.similarity >= 98.0,
            "Crbug884166GM similarity ${"%.2f".format(comparison.similarity)}% < 98.0% (t=1 floor)")
    }
}
