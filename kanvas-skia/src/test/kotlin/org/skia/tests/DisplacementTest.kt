package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DisplacementTest {
    @Test
    fun `DisplacementGM matches displacement_png within tolerance`() {
        val gm = DisplacementGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image displacement.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DisplacementGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DisplacementGM", comparison.similarity)
        assertTrue(accepted, "DisplacementGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "DisplacementGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
