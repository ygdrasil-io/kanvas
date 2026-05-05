package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug615686Test {

    @Test
    fun `Bug615686GM matches bug615686_png within tolerance`() {
        val gm = Bug615686GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug615686.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug615686GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug615686GM", comparison.similarity)
        assertTrue(accepted, "Bug615686GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 90.0,
            "Bug615686GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
