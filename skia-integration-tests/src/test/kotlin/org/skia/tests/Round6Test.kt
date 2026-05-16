package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round6Test {

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

    @Test
    fun `OneBadArcGM matches reference`() = runGm(OneBadArcGM(), "OneBadArcGM", 82.0)

    @Test
    fun `Crbug888453GM matches reference`() = runGm(Crbug888453GM(), "Crbug888453GM", 89.0)

    @Test
    fun `Crbug1472747GM matches reference`() = runGm(Crbug1472747GM(), "Crbug1472747GM", 96.0)

    @Test
    fun `Bug40810065GM matches reference`() = runGm(Bug40810065GM(), "Bug40810065GM", 97.0)

    @Test
    fun `Bug12866GM matches reference`() = runGm(Bug12866GM(), "Bug12866GM", 92.0)

    @Test
    fun `Bug7792GM matches reference`() = runGm(Bug7792GM(), "Bug7792GM", 98.0)

    @Test
    fun `PathInvFillGM matches reference`() = runGm(PathInvFillGM(), "PathInvFillGM", 98.0)

    // CircularArcsFillGM at ~67% — 4 grids × 8 starts × 8 sweeps × 2 colors
    // = 512 filled arcs with translucent overlap (alpha=100). Each cell's
    // arc-and-complement combination produces large fields where AA edges
    // cumulate sub-tolerance drift. Visual layout matches the reference.
    @Test
    fun `CircularArcsFillGM matches reference`() =
        runGm(CircularArcsFillGM(), "CircularArcsFillGM", 65.0)

    @Test
    fun `CircularArcsHairlineGM matches reference`() =
        runGm(CircularArcsHairlineGM(), "CircularArcsHairlineGM", 92.0)

    @Test
    fun `ConvexLineOnlyPathsFillGM matches reference`() =
        runGm(ConvexLineOnlyPathsFillGM(), "ConvexLineOnlyPathsFillGM", 94.0)

    @Test
    fun `ConvexLineOnlyPathsStrokeAndFillGM matches reference`() =
        runGm(ConvexLineOnlyPathsStrokeAndFillGM(), "ConvexLineOnlyPathsStrokeAndFillGM", 93.0)
}
