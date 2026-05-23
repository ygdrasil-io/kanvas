package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug41422450Test {

    @Test
    fun `Bug41422450GM matches bug41422450_png within tolerance`() {
        val gm = Bug41422450GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug41422450.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug41422450GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug41422450GM", comparison.similarity)
        assertTrue(accepted, "Bug41422450GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 98.0,
            "Bug41422450GM similarity ${"%.2f".format(comparison.similarity)}% < 98.0% floor",
        )
    }
}
