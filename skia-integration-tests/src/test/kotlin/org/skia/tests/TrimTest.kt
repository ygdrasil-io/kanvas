package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TrimTest {

    @Test
    fun `TrimGM matches trimpatheffect_png within tolerance`() {
        val gm = TrimGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image trimpatheffect.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TrimGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TrimGM", comparison.similarity)
        assertTrue(accepted, "TrimGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "TrimGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%",
        )
    }
}
