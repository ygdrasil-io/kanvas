package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Skbug14554Test {

    @Test
    fun `Skbug14554GM matches reference`() {
        val gm = Skbug14554GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Skbug14554GM", comparison)
        if (comparison.similarity < 40.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("Skbug14554GM", comparison.similarity))
        assertTrue(
            comparison.similarity >= 35.0,
            "Skbug14554GM similarity ${"%.2f".format(comparison.similarity)}% < 35.0% floor",
        )
    }
}
