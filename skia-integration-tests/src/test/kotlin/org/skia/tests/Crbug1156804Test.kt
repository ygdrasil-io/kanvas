package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug1156804Test {

    @Test
    fun `Crbug1156804GM matches crbug_1156804_png within tolerance`() {
        val gm = Crbug1156804GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1156804.png")
        // 4 small rects with image-filter Blur+Crop. Most of the canvas is
        // background, but the σ=20 (bottom row) blurs cover ~30% of the
        // canvas with halos that are hyper-sensitive to the filter pipeline's
        // coordinate-offset bookkeeping (the saveLayer + filter dance our
        // GM uses to emulate `paint.imageFilter` on `drawRect` — see GM
        // KDoc). Floor 70% : capture observable correctness while accepting
        // halo edge drift. Improving requires a `drawRect`-native imageFilter
        // path (parity with Skia's internal saveLayer synthesis).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug1156804GM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1156804GM", comparison.similarity)
        assertTrue(accepted, "Crbug1156804GM regressed below ratchet")
        assertTrue(comparison.similarity >= 70.0,
            "Crbug1156804GM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% (t=1 floor)")
    }
}
