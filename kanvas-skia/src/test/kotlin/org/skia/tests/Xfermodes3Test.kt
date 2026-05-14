package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Xfermodes3Test {

    @Test
    fun `Xfermodes3GM matches xfermodes3_png within tolerance`() {
        val gm = Xfermodes3GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image xfermodes3.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Xfermodes3GM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Xfermodes3GM", comparison.similarity)
        assertTrue(accepted, "Xfermodes3GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 90.0,
            "Xfermodes3GM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
