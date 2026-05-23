package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Tests for the first batch of missing `gm/gradients.cpp` GMs that use only
 * existing kanvas-skia APIs (no CSS gradient color space interpolation):
 *  - `gradient_many_stops`      — 200-stop linear gradient
 *  - `gradient_many_hard_stops` — 300-stop linear gradient with hard stops
 *  - `gradients_dup_color_stops` — dup stops at start / end / middle
 *  - `gradients_alpha_many_stops` — 13-stop fade from gray to transparent
 *  - `gradients_interesting` — 6-config × 3-tilemode linear gradient grid
 */
class GradientsMissingBatch1Test {

    private fun runGm(gm: GM, trackerName: String, floor: Double) {
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed(trackerName, comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore(trackerName, comparison.similarity)
        assertTrue(accepted, "$trackerName regressed below ratchet")
        assertTrue(
            comparison.similarity >= floor,
            "$trackerName similarity ${"%.2f".format(comparison.similarity)}% < $floor% floor",
        )
    }

    @Test
    fun `gradient_many_stops matches reference`() =
        runGm(GradientManyStopsGM(), "GradientManyStopsGM", 80.0)

    @Test
    fun `gradient_many_hard_stops matches reference`() =
        runGm(GradientManyHardStopsGM(), "GradientManyHardStopsGM", 80.0)

    @Test
    fun `gradients_dup_color_stops matches reference`() =
        runGm(GradientsDupColorStopsGM(), "GradientsDupColorStopsGM", 60.0)

    @Test
    fun `gradients_alpha_many_stops matches reference`() =
        runGm(GradientsAlphaManyStopsGM(), "GradientsAlphaManyStopsGM", 80.0)

    @Test
    fun `gradients_interesting matches reference`() =
        runGm(GradientsInterestingGM(), "GradientsInterestingGM", 80.0)
}
