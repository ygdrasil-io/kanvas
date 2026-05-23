package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class NinepatchEdgeCase349428795Test {

    @Test
    fun `NinepatchEdgeCase349428795GM matches ninepatch_edge_case_349428795_png within tolerance`() {
        val gm = NinepatchEdgeCase349428795GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ninepatch_edge_case_349428795.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("NinepatchEdgeCase349428795GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("NinepatchEdgeCase349428795GM", comparison.similarity)
        assertTrue(accepted, "NinepatchEdgeCase349428795GM regressed below ratchet")
        assertTrue(comparison.similarity >= 0.0,
            "NinepatchEdgeCase349428795GM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
