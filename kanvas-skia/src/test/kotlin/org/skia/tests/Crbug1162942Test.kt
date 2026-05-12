package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Phase G5 GM port — see [Crbug1162942GM] for source-spec mapping.
 *
 * Exercises [org.skia.core.SkCanvas.experimental_DrawEdgeAAQuad] under a
 * perspective CTM at high translate offsets. The GM was authored to
 * stress a GPU-side AA inset-vs-3D-edge solver that doesn't exist on
 * raster — upstream's `SkDevice::drawEdgeAAQuad` and our port both
 * shortcut to all-or-nothing AA, so the reference and port should align
 * closely.
 */
class Crbug1162942Test {

    @Test
    fun `Crbug1162942GM matches crbug_1162942_png within tolerance`() {
        val gm = Crbug1162942GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1162942.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Crbug1162942GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1162942GM", comparison.similarity)
        assertTrue(accepted, "Crbug1162942GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 90.0,
            "Crbug1162942GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
