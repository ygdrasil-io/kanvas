package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class Xfermodes2Test {

    @Test
    fun `Xfermodes2GM matches xfermodes2_png within tolerance`() {
        val gm = Xfermodes2GM(grayscale = false)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image xfermodes2.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("Xfermodes2GM", comparison)
        if (comparison.similarity < 50.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("Xfermodes2GM", comparison.similarity)
        assertTrue(accepted, "Xfermodes2GM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 50.0,
            "Xfermodes2GM similarity ${"%.2f".format(comparison.similarity)}% < 50.0% floor",
        )
    }
}
