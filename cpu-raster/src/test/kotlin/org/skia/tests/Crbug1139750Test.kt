package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug1139750Test {

    @Test
    fun `Crbug1139750GM matches crbug_1139750_png within tolerance`() {
        val gm = Crbug1139750GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1139750.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug1139750GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1139750GM", comparison.similarity)
        assertTrue(accepted, "Crbug1139750GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 85.0,
            "Crbug1139750GM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% floor",
        )
    }
}
