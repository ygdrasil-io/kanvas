package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug12244Test {
    @Test
    fun `Skbug12244GM matches skbug12244_png within tolerance`() {
        val gm = Skbug12244GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug12244.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Skbug12244GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug12244GM", comparison.similarity)
        assertTrue(accepted, "Skbug12244GM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "Skbug12244GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
