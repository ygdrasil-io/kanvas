package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class BlursTest {

    @Test
    fun `BlursGM matches blurs_png within tolerance`() {
        val gm = BlursGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image blurs.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("BlursGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("BlursGM", comparison.similarity)
        assertTrue(accepted, "BlursGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "BlursGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
