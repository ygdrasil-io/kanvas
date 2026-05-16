package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug6987Test {

    @Test
    fun `Bug6987GM matches bug6987_png within tolerance`() {
        val gm = Bug6987GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug6987.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug6987GM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug6987GM", comparison.similarity)
        assertTrue(accepted, "Bug6987GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 98.0,
            "Bug6987GM similarity ${"%.2f".format(comparison.similarity)}% < 98.0% floor",
        )
    }
}
