package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round9Test {

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

    // Tier 1 — shallow gradient variants. Our pipeline doesn't apply
    // dither, so the dither variants will diverge from upstream's
    // dithered reference (kept for tracking).

    @Test
    fun `ShallowGradientSweepNoDitherGM matches reference`() =
        runGm(ShallowGradientSweepNoDitherGM(), "ShallowGradientSweepNoDitherGM", 90.0)

    @Test
    fun `ShallowGradientSweepDitherGM matches reference`() =
        runGm(ShallowGradientSweepDitherGM(), "ShallowGradientSweepDitherGM", 50.0)

    @Test
    fun `ShallowGradientConicalNoDitherGM matches reference`() =
        runGm(ShallowGradientConicalNoDitherGM(), "ShallowGradientConicalNoDitherGM", 90.0)

    @Test
    fun `ShallowGradientConicalDitherGM matches reference`() =
        runGm(ShallowGradientConicalDitherGM(), "ShallowGradientConicalDitherGM", 50.0)

    // Tier 1 — 2-pt conical outside / edge variants.

    @Test
    fun `ConicalGradients2ptOutsideNoDitherGM matches reference`() =
        runGm(ConicalGradients2ptOutsideNoDitherGM(), "ConicalGradients2ptOutsideNoDitherGM", 70.0)


    @Test
    fun `ConicalGradients2ptEdgeNoDitherGM matches reference`() =
        runGm(ConicalGradients2ptEdgeNoDitherGM(), "ConicalGradients2ptEdgeNoDitherGM", 70.0)

    @Test
    fun `ConicalGradients2ptEdgeGM matches reference`() =
        runGm(ConicalGradients2ptEdgeGM(), "ConicalGradients2ptEdgeGM", 50.0)

    @Test
    fun `Bug6783GM matches reference`() = runGm(Bug6783GM(), "Bug6783GM", 50.0)
}
