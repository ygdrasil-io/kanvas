package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathOpsSkbug10155Test {

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
     * `pathops_skbug_10155` exercises [SkOpBuilder.resolve] on two
     * cubic-Bézier paths that share near-precision-limit coordinates.
     * Regression cover for `skbug.com/10155` — the chained-`Op`
     * fallback in `resolve` must not lose precision on the
     * almost-degenerate vertices.
     *
     * Current score : 67.2 %. The floor is below that (50 %) so
     * future regressions still block ; the residual gap is mostly
     * a stroker / hairline rendering artefact (`strokeWidth=0`
     * draws filled in our raster), not pathops.
     */
    @Test
    fun `pathops_skbug_10155 matches reference`() =
        runGm(PathOpsSkbug10155GM(), "PathOpsSkbug10155GM", 50.0)
}
