package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round8Test {

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
    fun `ClipLargeRectGM matches reference`() = runGm(ClipLargeRectGM(), "ClipLargeRectGM", 98.0)

    @Test
    fun `ThinRoundRectsGM matches reference`() =
        runGm(ThinRoundRectsGM(), "ThinRoundRectsGM", 91.0)

    @Test
    fun `InnerShapesAaGM matches reference`() = runGm(InnerShapesAaGM(), "InnerShapesAaGM", 80.0)

    @Test
    fun `InnerShapesBwGM matches reference`() = runGm(InnerShapesBwGM(), "InnerShapesBwGM", 81.0)

}
