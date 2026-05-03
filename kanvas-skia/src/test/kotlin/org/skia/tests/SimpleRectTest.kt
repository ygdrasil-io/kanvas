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
        // SkRandom is bit-compatible with Skia and we now render into the DM
        // reference colorspace, so all 10 000 RGB565-quantised rects match
        // their reference value within 1 ulp per channel.
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 1)
        if (similarity < 99.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("SimpleRectGM", similarity)
        assertTrue(accepted, "SimpleRectGM regressed below tolerance")
        assertTrue(similarity >= 99.0,
            "SimpleRectGM similarity ${"%.2f".format(similarity)}% < 99.0% (t=1 floor)")
    }
}
