package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestUtils

class ConcavePathsTest {

    @Test
    fun `ConcavePathsGM matches concavepaths_png within tolerance`() {
        val gm = ConcavePathsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image concavepaths.png")
        // tolerance=160 keeps the same envelope used since Phase 1 to absorb
        // the wide-gamut working-space shift in the reference PNGs.
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 160)
        if (similarity < 90.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("ConcavePathsGM", similarity)
        assertTrue(accepted, "ConcavePathsGM regressed below ratchet")
        assertTrue(similarity >= 90.0,
            "ConcavePathsGM similarity ${"%.2f".format(similarity)}% < 90.0% (Phase 3 floor, t=160)")
    }
}
