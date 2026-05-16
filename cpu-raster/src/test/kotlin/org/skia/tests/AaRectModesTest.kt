package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class AaRectModesTest {

    @Test
    fun `AaRectModesGM matches aarectmodes_png within tolerance`() {
        val gm = AaRectModesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image aarectmodes.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("AaRectModesGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("AaRectModesGM", comparison.similarity)
        assertTrue(accepted, "AaRectModesGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 79.0,
            "AaRectModesGM similarity ${"%.2f".format(comparison.similarity)}% < 79.0% floor",
        )
    }
}
