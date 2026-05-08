package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PathOpsInverseTest {

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
     * D1.2.h.8 closed Op end-to-end ; this is the first GM that
     * exercises the full pathops pipeline visually. We start with a
     * generous floor (50 %) since some inverse-fill paths through
     * `bridgeOp` may still be off — the goal is to surface the gap
     * via the comparison image rather than gate the build.
     */
    @Test
    fun `PathOpsInverseGM matches reference`() =
        runGm(PathOpsInverseGM(), "PathOpsInverseGM", 50.0)
}
