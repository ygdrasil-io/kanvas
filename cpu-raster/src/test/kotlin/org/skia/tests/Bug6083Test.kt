package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug6083Test {

    @Test
    fun `Bug6083GM matches bug6083_png within tolerance`() {
        val gm = Bug6083GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug6083.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug6083GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug6083GM", comparison.similarity)
        assertTrue(accepted, "Bug6083GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 80.0,
            "Bug6083GM similarity ${"%.2f".format(comparison.similarity)}% < 80.0% floor",
        )
    }
}
