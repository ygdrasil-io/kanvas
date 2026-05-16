package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawableTest {

    @Test
    fun `DrawableGM matches drawable_png within tolerance`() {
        val gm = DrawableGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drawable.png")
        // Four drawable instances each draw an opaque blue control-bbox
        // rect with a thin AA white-filled conic curve overlay. The
        // conic-curve coverage AA accumulates floating-point fill widths
        // along the rasterised edges, so we accept a small per-pixel
        // tolerance (= 1) to absorb 1-LSB drift in the colour-space
        // round-trip without masking a real regression.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawableGM", comparison)
        if (comparison.similarity < 98.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawableGM", comparison.similarity)
        assertTrue(accepted, "DrawableGM regressed below ratchet")
        assertTrue(comparison.similarity >= 98.0,
            "DrawableGM similarity ${"%.2f".format(comparison.similarity)}% < 98.0% (t=1 floor)")
    }
}
