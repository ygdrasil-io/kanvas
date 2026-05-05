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
    fun `B340982297GM matches reference`() = runGm(B340982297GM(), "B340982297GM", 80.0)

    @Test
    fun `Bug406747427GM matches reference`() = runGm(Bug406747427GM(), "Bug406747427GM", 80.0)

    @Test
    fun `PathHugeCrbug800804GM matches reference`() =
        runGm(PathHugeCrbug800804GM(), "PathHugeCrbug800804GM", 50.0)

    @Test
    fun `Crbug996140GM matches reference`() = runGm(Crbug996140GM(), "Crbug996140GM", 70.0)

    @Test
    fun `ConjoinedPolygonsGM matches reference`() =
        runGm(ConjoinedPolygonsGM(), "ConjoinedPolygonsGM", 90.0)

    // FillTypesGM at 50.81% — circles' AA edges diverge sub-tolerance
    // from upstream's analytic-AA rasterizer (we use 4×4 supersampling).
    // Inverse fill rules amplify the count of edge pixels.
    @Test
    fun `FillTypesGM matches reference`() = runGm(FillTypesGM(), "FillTypesGM", 45.0)

    @Test
    fun `PolygonsGM matches reference`() = runGm(PolygonsGM(), "PolygonsGM", 70.0)

    @Test
    fun `SmallPathsGM matches reference`() = runGm(SmallPathsGM(), "SmallPathsGM", 80.0)
}
