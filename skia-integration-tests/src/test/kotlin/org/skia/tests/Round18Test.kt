package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round18Test {

    private fun runGm(gm: org.skia.tests.GM, trackerName: String, floor: Double) {
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

    // circular_arcs_weird: pathological arc parameters (degenerate ovals,
    // large/negative starts, ±360/±540 sweeps). Five paint styles across
    // 7 arc configs drawn twice (useCenter on/off). Dash effect on the
    // last paint is a no-op at raster time (pathEffect slot accepted but
    // not yet applied by the rasteriser); expect moderate similarity.
    @Test
    fun `CircularArcsWeirdGM matches reference`() =
        runGm(CircularArcsWeirdGM(), "CircularArcsWeirdGM", 50.0)

}
