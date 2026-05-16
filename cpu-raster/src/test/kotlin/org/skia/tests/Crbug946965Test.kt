package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug946965Test {

    @Test
    fun `Crbug946965GM matches crbug_946965_png within tolerance`() {
        val gm = Crbug946965GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_946965.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug946965GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug946965GM", comparison.similarity)
        assertTrue(accepted, "Crbug946965GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 85.0,
            "Crbug946965GM similarity ${"%.2f".format(comparison.similarity)}% < 85.0% floor",
        )
    }
}
