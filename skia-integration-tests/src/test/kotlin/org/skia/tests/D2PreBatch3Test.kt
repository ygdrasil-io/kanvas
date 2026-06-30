package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Third batch of GM ports — 3 more GMs that don't need any of the
 * unshipped chantiers. Floors at 0 % per the "as many as possible,
 * accept low %" directive.
 */
class D2PreBatch3Test {

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

    /** `small_color_stop` — gradient with very-close stop positions. */
    @Test
    fun `SmallColorStopGM matches reference`() =
        runGm(SmallColorStopGM(), "SmallColorStopGM", floor = 0.0)

    /** `gradients` — 6×5 grid of gradient permutations. */
    @Test
    fun `GradientsGM matches reference`() =
        runGm(GradientsGM(), "GradientsGM", floor = 0.0)
}
