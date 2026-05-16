package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class HugePathCrbug800804Test {

    @Test
    fun `HugePathCrbug800804GM matches path_huge_crbug_800804_png within tolerance`() {
        val gm = HugePathCrbug800804GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image path_huge_crbug_800804.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("HugePathCrbug800804GM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("HugePathCrbug800804GM", comparison.similarity)
        assertTrue(accepted, "HugePathCrbug800804GM regressed below ratchet")
    }
}
