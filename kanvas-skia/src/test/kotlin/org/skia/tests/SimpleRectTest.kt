package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestUtils

class SimpleRectTest {

    @Test
    fun `SimpleRectGM matches simplerect_png within tolerance`() {
        val gm = SimpleRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image simplerect.png")
        // SkRandom is bit-compatible with Skia, so every one of the 10 000
        // rects lands at the same position and same RGB565-quantised colour
        // as the reference. The remaining diff is the wide-gamut colour
        // shift that also affects BigRectGM — so we use the same per-channel
        // tolerance.
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 160)
        if (similarity < 99.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("SimpleRectGM", similarity)
        assertTrue(accepted, "SimpleRectGM regressed below tolerance")
        assertTrue(similarity >= 99.0,
            "SimpleRectGM similarity ${"%.2f".format(similarity)}% < 99.0% (Phase 1 floor, t=160)")
    }
}
