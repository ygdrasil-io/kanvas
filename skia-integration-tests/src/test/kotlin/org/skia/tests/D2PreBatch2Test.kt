package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

/**
 * Second batch of GM ports — 4 more GMs that don't need any of the
 * unshipped chantiers (D2 / D1.4 / etc.) and compose only over
 * already-shipped primitives. Floors at 0 % per the "as many as
 * possible, accept low %" directive.
 */
class D2PreBatch2Test {

    private fun runGm(gm: GM, trackerName: String, floor: Double) {
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

    /**
     * `badpaint` — invalid paints (empty bitmap shader / non-invertible
     * local matrix). Should produce only the canvas background.
     */
    @Test
    fun `BadPaintGM matches reference`() =
        runGm(BadPaintGM(), "BadPaintGM", floor = 0.0)

    /**
     * `emptyshader` — 5 cells, each carrying a degenerate shader that
     * should fall back to "empty" (no draw).
     */
    @Test
    fun `EmptyShaderGM matches reference`() =
        runGm(EmptyShaderGM(), "EmptyShaderGM", floor = 0.0)

    /**
     * `filltypespersp` — 4×4 grid of fill-type permutations drawn
     * in perspective with a radial-gradient background.
     */
    @Test
    fun `FillTypePerspGM matches reference`() =
        runGm(FillTypePerspGM(), "FillTypePerspGM", floor = 0.0)
}
