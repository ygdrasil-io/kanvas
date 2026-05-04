package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Bug593049Test {
    @Test
    fun `Bug593049GM matches bug593049_png within tolerance`() {
        val gm = Bug593049GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image bug593049.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("Bug593049GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Bug593049GM", comparison.similarity)
        assertTrue(accepted, "Bug593049GM regressed below ratchet")
        assertTrue(comparison.similarity >= 90.0,
            "Bug593049GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0%")
    }
}
