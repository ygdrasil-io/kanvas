package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ArcCircleGapTest {
    @Test
    fun `ArcCircleGapGM matches arccirclegap_png within tolerance`() {
        val gm = ArcCircleGapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image arccirclegap.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ArcCircleGapGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ArcCircleGapGM", comparison.similarity)
        assertTrue(accepted, "ArcCircleGapGM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "ArcCircleGapGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
