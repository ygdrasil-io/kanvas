package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug10141204Test {

    @Test
    fun `Crbug10141204GM matches crbug_10141204_png within tolerance`() {
        val gm = Crbug10141204GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_10141204.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug10141204GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug10141204GM", comparison.similarity)
        assertTrue(accepted, "Crbug10141204GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 50.0,
            "Crbug10141204GM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% floor",
        )
    }
}
