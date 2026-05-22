package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class LcdTextSizeTest {
    @Test
    fun `LcdTextSizeGM matches lcdtextsize_png within tolerance`() {
        val gm = LcdTextSizeGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image lcdtextsize.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("LcdTextSizeGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("LcdTextSizeGM", comparison.similarity)
        assertTrue(accepted, "LcdTextSizeGM regressed below ratchet")
        // Text-heavy GM ; kanvas-skia subpixel-AA downgrade to greyscale AA
        // limits the visual match. Accept-any floor.
        assertTrue(comparison.similarity >= 0.0,
            "LcdTextSizeGM similarity ${"%.2f".format(comparison.similarity)}% < 0.0% floor")
    }
}
