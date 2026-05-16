package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug947055Test {

    @Test
    fun `Crbug947055GM matches crbug_947055_png within tolerance`() {
        val gm = Crbug947055GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_947055.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug947055GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug947055GM", comparison.similarity)
        assertTrue(accepted, "Crbug947055GM regressed below tolerance")
        // Floor 95 % — the perspective branch in `buildEdges` (Phase
        // 6m) projects path control points correctly under the 3 × 3
        // CTM. Phase 6s `eraseColor` colorspace xform fix lifted this
        // GM from 14.6 % → 96.6 %, retroactively validating that the
        // perspective rasterizer was correct all along — the residual
        // drift was entirely the unxformed BG colour.
        assertTrue(
            comparison.similarity >= 95.0,
            "Crbug947055GM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
