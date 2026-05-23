package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Visual regression test for [DropShadowPseudoPerspGM].
 *
 * The GM exercises drop-shadow layer-bounds computation under a
 * pseudo-perspective 4×4 matrix (canvas matrix has non-zero Z impact
 * but no visible distortion). Floor is 0 % — this is a new port;
 * upstream reference `dropshadow_pseudopersp.png` will show drift
 * vs. GPU Skia due to our CPU-only rendering path through the
 * perspective matrices.
 */
class DropShadowPseudoPerspTest {

    private fun runGm(gm: GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below tolerance")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `DropShadowPseudoPerspGM matches reference`() =
        runGm(DropShadowPseudoPerspGM(), "DropShadowPseudoPerspGM", floor = 0.0)
}
