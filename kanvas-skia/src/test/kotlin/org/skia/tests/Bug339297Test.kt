package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug339297Test {

    @Test
    fun `Bug339297GM matches bug339297_png within tolerance`() {
        val gm = Bug339297GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug339297.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug339297GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug339297GM", comparison.similarity)
        assertTrue(accepted, "Bug339297GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 50.0,
            "Bug339297GM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% floor",
        )
    }
}
