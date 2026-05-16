package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug4868Test {

    @Test
    fun `Skbug4868GM matches skbug_4868_png within tolerance`() {
        val gm = Skbug4868GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image skbug_4868.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Skbug4868GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Skbug4868GM", comparison.similarity)
        assertTrue(accepted, "Skbug4868GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 98.0,
            "Skbug4868GM similarity ${"%.2f".format(comparison.similarity)}% < 98.0% floor",
        )
    }
}
