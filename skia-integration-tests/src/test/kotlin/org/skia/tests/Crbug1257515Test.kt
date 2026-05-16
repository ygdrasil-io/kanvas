package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug1257515Test {

    @Test
    fun `Crbug1257515GM matches crbug_1257515_png within tolerance`() {
        val gm = Crbug1257515GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_1257515.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug1257515GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug1257515GM", comparison.similarity)
        assertTrue(accepted, "Crbug1257515GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 97.0,
            "Crbug1257515GM similarity ${"%.2f".format(comparison.similarity)}% < 97.0% floor",
        )
    }
}
