package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug938592Test {

    @Test
    fun `Crbug938592GM matches crbug_938592_png within tolerance`() {
        val gm = Crbug938592GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_938592.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug938592GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug938592GM", comparison.similarity)
        assertTrue(accepted, "Crbug938592GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 70.0,
            "Crbug938592GM similarity ${"%.2f".format(comparison.similarity)}% < 70.0% floor",
        )
    }
}
