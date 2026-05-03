package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestUtils

class ThinRectsTest {

    @Test
    fun `ThinRectsGM matches thinrects_png within tolerance`() {
        val gm = ThinRectsGM(fRound = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image thinrects.png")
        // Rendered in the DM reference colorspace so most pixels match
        // bit-exactly. The residual ~8% at t=1 is the AA coverage→alpha
        // quantization rounding (the gap closes to ~2% at t=16, ~0% at t=32).
        // Reproducing Skia's exact `SkScan_Antihair.cpp` rounding is an
        // optimization for a later slice.
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 1)
        if (similarity < 92.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("ThinRectsGM", similarity)
        assertTrue(accepted, "ThinRectsGM regressed below ratchet")
        assertTrue(similarity >= 92.0,
            "ThinRectsGM similarity ${"%.2f".format(similarity)}% < 92.0% (t=1 floor)")
    }
}
