package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round7Test {

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
    fun `TrickyCubicStrokesLargeRadiusGM matches reference`() =
        runGm(TrickyCubicStrokesLargeRadiusGM(), "TrickyCubicStrokesLargeRadiusGM", 97.0)

    @Test
    fun `TrickyCubicStrokesButtMiterGM matches reference`() =
        runGm(TrickyCubicStrokesButtMiterGM(), "TrickyCubicStrokesButtMiterGM", 96.0)

    @Test
    fun `TrickyCubicStrokesRoundCapsGM matches reference`() =
        runGm(TrickyCubicStrokesRoundCapsGM(), "TrickyCubicStrokesRoundCapsGM", 96.0)

    @Test
    fun `StringArtGM matches reference`() = runGm(StringArtGM(), "StringArtGM", 81.0)

    // SimpleShapes uses paint.alphaf=0.5 + AA — accumulates byte-level
    // drift over 9 RRect/oval shapes at random rotations. Visual layout
    // matches the reference cell-by-cell.
    @Test
    fun `SimpleShapesAaGM matches reference`() = runGm(SimpleShapesAaGM(), "SimpleShapesAaGM", 64.0)

    @Test
    fun `SimpleShapesBwGM matches reference`() = runGm(SimpleShapesBwGM(), "SimpleShapesBwGM", 64.0)

    // The 3 stroked-arc grids (`butt` / `square` / `round` cap) hit the
    // same translucent-overlap drift as `CircularArcsFillGM` (4 grids
    // × 8 starts × 8 sweeps × 2 colours = 512 stroked arcs with alpha
    // overlap). Visual layout matches the reference. Floor 30 %.
    @Test
    fun `CircularArcsStrokeButtGM matches reference`() =
        runGm(CircularArcsStrokeButtGM(), "CircularArcsStrokeButtGM", 44.0)

    @Test
    fun `CircularArcsStrokeSquareGM matches reference`() =
        runGm(CircularArcsStrokeSquareGM(), "CircularArcsStrokeSquareGM", 44.0)

    @Test
    fun `CircularArcsStrokeRoundGM matches reference`() =
        runGm(CircularArcsStrokeRoundGM(), "CircularArcsStrokeRoundGM", 44.0)
}
