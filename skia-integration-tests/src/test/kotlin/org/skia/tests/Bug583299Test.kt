package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug583299Test {
    @Test
    fun `Bug583299GM matches bug583299_png within tolerance`() {
        val gm = Bug583299GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug583299.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug583299GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug583299GM", comparison.similarity)
        assertTrue(accepted, "Bug583299GM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 90.0,
            "Bug583299GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%",
        )
    }
}
