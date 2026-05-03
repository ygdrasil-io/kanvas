package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestUtils

class ClipStrokeRectTest {

    @Test
    fun `ClipStrokeRectGM matches clip_strokerect_png within tolerance`() {
        val gm = ClipStrokeRectGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clip_strokerect.png")
        // Rendered in the DM reference colorspace; matches bit-exactly within
        // 1 ulp per channel.
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 1)
        if (similarity < 99.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("ClipStrokeRectGM", similarity)
        assertTrue(accepted, "ClipStrokeRectGM regressed below ratchet")
        assertTrue(similarity >= 99.0,
            "ClipStrokeRectGM similarity ${"%.2f".format(similarity)}% < 99.0% (t=1 floor)")
    }
}
