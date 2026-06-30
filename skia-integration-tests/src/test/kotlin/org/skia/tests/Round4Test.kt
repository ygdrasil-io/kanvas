package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Combined test class for round 4 GM ports — keeps the test list flat
 * (one test method per GM) without spawning 9 separate test files.
 */
class Round4Test {

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
    fun `Bug406747427GM matches reference`() = runGm(Bug406747427GM(), "Bug406747427GM", 96.0)

    @Test
    fun `PathHugeCrbug800804GM matches reference`() =
        runGm(PathHugeCrbug800804GM(), "PathHugeCrbug800804GM", 88.0)

    @Test
    fun `Crbug996140GM matches reference`() = runGm(Crbug996140GM(), "Crbug996140GM", 70.0)

    @Test
    fun `ConjoinedPolygonsGM matches reference`() =
        runGm(ConjoinedPolygonsGM(), "ConjoinedPolygonsGM", 98.0)

    // FillTypesGM lifted from 50.81 % → 99.48 % by Phase 6s `eraseColor`
    // colorspace xform — the residual sub-tolerance edge drift is now
    // dominated by the (correct) AA-edge supersampling, not BG bias.
    @Test
    fun `FillTypesGM matches reference`() = runGm(FillTypesGM(), "FillTypesGM", 98.0)

    @Test
    fun `PolygonsGM matches reference`() = runGm(PolygonsGM(), "PolygonsGM", 85.0)

    @Test
    fun `SmallPathsGM matches reference`() = runGm(SmallPathsGM(), "SmallPathsGM", 96.0)
}
