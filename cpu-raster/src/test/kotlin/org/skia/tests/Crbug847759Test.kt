package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug847759Test {

    @Test
    fun `Crbug847759GM matches crbug_847759_png within tolerance`() {
        val gm = Crbug847759GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image crbug_847759.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Crbug847759GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug847759GM", comparison.similarity)
        assertTrue(accepted, "Crbug847759GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 90.0,
            "Crbug847759GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
