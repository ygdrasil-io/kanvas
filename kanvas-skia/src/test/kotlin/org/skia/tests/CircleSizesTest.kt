package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class CircleSizesTest {
    @Test
    fun `CircleSizesGM matches circle_sizes_png within tolerance`() {
        val gm = CircleSizesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image circle_sizes.png")
        // 16 AA circles with radii 1..16 in a 4×4 grid. Tiny radii (1-2)
        // exercise the rasterizer's degenerate disk path.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("CircleSizesGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("CircleSizesGM", comparison.similarity)
        assertTrue(accepted, "CircleSizesGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "CircleSizesGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% (t=1 floor)")
    }
}
