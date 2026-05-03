package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestUtils

class BigRectTest {

    @Test
    fun `BigRectGM matches bigrect_png within tolerance`() {
        val gm = BigRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bigrect.png")
        // We render into the DM reference colorspace (Rec.2020 with the exact
        // ICC profile from `bigrect.png`), so colors land 1-ulp away from the
        // reference instead of ~150 ulp. The remaining ~4.5% non-matching
        // pixels at t=1 are structural disagreements between our non-AA rect
        // rasterizer and Skia's on the extreme-coordinate cells (1e6 / 1e10
        // sized rects clipped to a 35x35 viewport).
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 1)
        if (similarity < 95.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("BigRectGM", similarity)
        assertTrue(accepted, "BigRectGM regressed below tolerance")
        assertTrue(similarity >= 95.0,
            "BigRectGM similarity ${"%.2f".format(similarity)}% < 95.0% (t=1 floor)")
    }
}
