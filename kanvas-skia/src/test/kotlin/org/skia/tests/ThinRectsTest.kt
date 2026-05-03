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
        // Wide-gamut working space: same colour shift as BigRect/SimpleRect
        // (cf. Phase 1 note in MIGRATION_PLAN.md). tolerance=160 covers the
        // worst-case per-channel offset; AA coverage adds further sub-pixel
        // diff but stays inside the same envelope.
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 160)
        if (similarity < 95.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("ThinRectsGM", similarity)
        assertTrue(accepted, "ThinRectsGM regressed below ratchet")
        assertTrue(similarity >= 95.0,
            "ThinRectsGM similarity ${"%.2f".format(similarity)}% < 95.0% (Phase 2 floor, t=160)")
    }
}
