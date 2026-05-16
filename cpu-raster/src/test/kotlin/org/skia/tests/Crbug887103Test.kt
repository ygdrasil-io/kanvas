package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug887103Test {

    @Test
    fun `Crbug887103GM matches crbug_887103_png within tolerance`() {
        val gm = Crbug887103GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_887103.png")
        // AA fill of three nearly-degenerate line-only triangles. Geometry
        // is pixel-aligned only at the three start vertices, so most edges
        // exercise the path scanline 4x4 supersampler. The residual is
        // sub-ulp coverage rounding on AA edges.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug887103GM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug887103GM", comparison.similarity)
        assertTrue(accepted, "Crbug887103GM regressed below ratchet")
        assertTrue(comparison.similarity >= 99.0,
            "Crbug887103GM similarity ${"%.2f".format(comparison.similarity)}% < 99.0% (t=1 floor)")
    }
}
