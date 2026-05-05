package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug5099Test {

    @Test
    fun `Bug5099GM matches bug5099_png within tolerance`() {
        val gm = Bug5099GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug5099.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug5099GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug5099GM", comparison.similarity)
        assertTrue(accepted, "Bug5099GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 94.0,
            "Bug5099GM similarity ${"%.2f".format(comparison.similarity)}% < 94.0% floor",
        )
    }
}
