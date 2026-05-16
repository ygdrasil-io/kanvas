package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Fifth batch of GM ports. Floors at 0 % per the "as many as
 * possible, accept low %" directive.
 */
class D2PreBatch5Test {

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

    /**
     * `nested_aa` — combinations of nested rect / rrect / oval
     * shapes with CW outer + CCW inner contours (winding rule
     * stress).
     */
    @Test
    fun `NestedGM matches reference`() =
        runGm(NestedGM(), "NestedGM", floor = 0.0)

    /**
     * `morphology` — Erode / Dilate filters at various radii on a
     * synthetic input bitmap (substitutes upstream's text input
     * since fonts aren't iso-portable).
     */
    @Test
    fun `MorphologyGM matches reference`() =
        runGm(MorphologyGM(), "MorphologyGM", floor = 0.0)
}
