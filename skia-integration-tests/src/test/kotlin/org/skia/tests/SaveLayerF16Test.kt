package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SaveLayerF16Test {

    @Test
    fun `SaveLayerF16GM matches savelayer_f16_png within tolerance`() {
        val gm = SaveLayerF16GM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image savelayer_f16.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("SaveLayerF16GM", comparison)
        if (comparison.similarity < EXPECTED_SIMILARITY) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("SaveLayerF16GM", comparison.similarity))
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "SaveLayerF16GM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY% floor",
        )
    }
}

private const val EXPECTED_SIMILARITY = 20.0
