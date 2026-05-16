package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class P3Test {

    @Test
    fun `P3GM matches p3_png within tolerance`() {
        val gm = P3GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        // The upstream `compare_pixel` text labels (which form the bulk
        // of the reference's non-rect pixels) are intentionally omitted ;
        // see the GM doc-comment for rationale. Similarity is therefore
        // structurally bounded.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("P3GM", comparison)
        val floor = 87.4
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("P3GM", comparison.similarity)
        assertTrue(accepted, "P3GM regressed below ratchet")
    }

    @Test
    fun `P3OvalsGM matches p3_ovals_png within tolerance`() {
        val gm = P3OvalsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 16)
        TestReport.recordDetailed("P3OvalsGM", comparison)
        val floor = 86.7
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("P3OvalsGM", comparison.similarity)
        assertTrue(accepted, "P3OvalsGM regressed below ratchet")
    }
}
