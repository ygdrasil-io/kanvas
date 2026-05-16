package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Strokes4Test {

    @Test
    fun `Strokes4GM matches strokes_zoomed_png within tolerance`() {
        val gm = Strokes4GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image strokes_zoomed.png")
        // A single circle stroked at width 0.055 under scale(1000, 1000):
        // device-space stroke ≈ 55 px on a circle of radius 1970 px.
        // Stresses the stroker on cubic Bézier curves at very large
        // device-space scale (the inverse of TeenyStrokesGM's tiny scales).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Strokes4GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Strokes4GM", comparison.similarity)
        assertTrue(accepted, "Strokes4GM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "Strokes4GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)")
    }
}
