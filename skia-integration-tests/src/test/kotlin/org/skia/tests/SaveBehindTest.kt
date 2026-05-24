package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class SaveBehindTest {

    @Test
    fun `SaveBehindGM matches save_behind_png within tolerance`() {
        val gm = SaveBehindGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image save_behind.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("SaveBehindGM", comparison)
        if (comparison.similarity < EXPECTED_SIMILARITY) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        assertTrue(SimilarityTracker.updateScore("SaveBehindGM", comparison.similarity))
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "SaveBehindGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY% floor",
        )
    }
}

private const val EXPECTED_SIMILARITY = 90.0
