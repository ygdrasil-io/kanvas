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
        // Rendered in the DM reference colorspace. Path scanline+4×4 SS
        // produces ~99% bit-exact at t=1; the residual ~1% is sub-ulp coverage
        // rounding on AA edges (closes to 100% at t=128).
        val similarity = TestUtils.compareBitmaps(rendered, reference!!, tolerance = 1)
        if (similarity < 98.0) {
            TestUtils.saveDebugImage(rendered, "${gm.name()}-rendered")
            TestUtils.saveDebugImage(reference, "${gm.name()}-reference")
        }
        val accepted = SimilarityTracker.updateScore("ConcavePathsGM", similarity)
        assertTrue(accepted, "ConcavePathsGM regressed below ratchet")
        assertTrue(similarity >= 98.0,
            "ConcavePathsGM similarity ${"%.2f".format(similarity)}% < 98.0% (t=1 floor)")
    }
}
