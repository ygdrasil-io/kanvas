package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DegenerateSegmentsTest {
    @Test
    fun `DegenerateSegmentsGM matches degeneratesegments_png within tolerance`() {
        val gm = DegenerateSegmentsGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image degeneratesegments.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DegenerateSegmentsGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DegenerateSegmentsGM", comparison.similarity)
        assertTrue(accepted, "DegenerateSegmentsGM regressed below ratchet")
        assertTrue(comparison.similarity >= 80.0,
            "DegenerateSegmentsGM similarity ${"%.2f".format(comparison.similarity)}% < 80.0%")
    }
}
