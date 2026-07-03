package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round5Test {

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
    fun `SquareHairGM matches reference`() = runGm(SquareHairGM(), "SquareHairGM", 95.0)

    @Test
    @Disabled("Missing upstream reference squarehair_diffs.png")
    fun `SquareHairDiffsGM matches reference`() =
        runGm(SquareHairDiffsGM(), "SquareHairDiffsGM", 95.0)

    @Test
    fun `ZeroControlStrokeGM matches reference`() =
        runGm(ZeroControlStrokeGM(), "ZeroControlStrokeGM", 98.0)

    @Test
    fun `HairlineSubdivGM matches reference`() =
        runGm(HairlineSubdivGM(), "HairlineSubdivGM", 95.0)

    @Test
    fun `InnerJoinGeometryGM matches reference`() =
        runGm(InnerJoinGeometryGM(), "InnerJoinGeometryGM", 96.0)

}
