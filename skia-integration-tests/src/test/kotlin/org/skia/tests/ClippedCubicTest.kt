package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClippedCubicTest {

    @Test
    fun `ClippedCubicGM matches clippedcubic_png within tolerance`() {
        val gm = ClippedCubicGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clippedcubic.png")
        // 3×3 grid: a self-intersecting cubic Bézier clipped to its own
        // bounding box, then translated by (dx, dy) ∈ {-1, 0, +1} px to
        // expose the rasterizer's clip-edge arithmetic on a curve that
        // is partially outside the clip.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ClippedCubicGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClippedCubicGM", comparison.similarity)
        assertTrue(accepted, "ClippedCubicGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "ClippedCubicGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)")
    }
}
