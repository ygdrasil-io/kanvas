package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase G5 GM port — see [Crbug1167277GM] for source-spec mapping.
 *
 * Exercises [org.skia.core.SkCanvas.experimental_DrawEdgeAAQuad] under a
 * perspective CTM with a 4-point clip. Per-edge AA is GPU-only in
 * upstream; both the reference and our raster port shortcut to all-or-
 * nothing AA, so the renders are expected to align closely.
 */
class Crbug1167277Test {

    @Test
    fun `Crbug1167277GM matches crbug_1167277_png within tolerance`() {
        val gm = Crbug1167277GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1167277.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Crbug1167277GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1167277GM", comparison.similarity)
        assertTrue(accepted, "Crbug1167277GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 90.0,
            "Crbug1167277GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
