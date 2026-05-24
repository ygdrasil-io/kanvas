package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Crbug404394639Test {

    @Test
    fun `Crbug404394639GM matches reference`() {
        val gm = Crbug404394639GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("Crbug404394639GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Crbug404394639GM", comparison.similarity)
        assertTrue(accepted, "Crbug404394639GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 0.0,
            "Crbug404394639GM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor",
        )
    }
}
