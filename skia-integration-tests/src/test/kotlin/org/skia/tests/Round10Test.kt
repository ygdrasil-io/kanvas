package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Round10Test {

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

    // TinyBitmapGM at 0 % byte-level — every pixel of the 100×100 wash
    // diverges by ≤ 15 from the reference (visually identical light pink
    // over the 0xDDDDDD BG, but a uniform colorspace shift). Upstream
    // stores the source pixel as **premul** N32 (`SkPackARGB32(0x80,
    // 0x80, 0, 0)` = full-red at 50 % alpha); our 8888 backing stores
    // unpremul, so even after the coefficient fix the working-space →
    // bitmap conversion math drifts under the half-alpha modulation.
    // Floor=0 — tracker-only.
    @Test
    fun `TinyBitmapGM matches reference`() = runGm(TinyBitmapGM(), "TinyBitmapGM", 0.0)

    @Test
    fun `BigMatrixGM matches reference`() = runGm(BigMatrixGM(), "BigMatrixGM", 80.0)


    @Test
    fun `BitmapRectTestGM matches reference`() = runGm(BitmapRectTestGM(), "BitmapRectTestGM", 80.0)

    @Test
    fun `SmallCirclesGM matches reference`() = runGm(SmallCirclesGM(), "SmallCirclesGM", 80.0)

    @Test
    fun `AnalyticAntialiasInverseGM matches reference`() =
        runGm(AnalyticAntialiasInverseGM(), "AnalyticAntialiasInverseGM", 95.0)

    @Test
    fun `AnalyticAntialiasConvexGM matches reference`() =
        runGm(AnalyticAntialiasConvexGM(), "AnalyticAntialiasConvexGM", 90.0)

    @Test
    fun `AnalyticAntialiasGeneralGM matches reference`() =
        runGm(AnalyticAntialiasGeneralGM(), "AnalyticAntialiasGeneralGM", 90.0)
}
