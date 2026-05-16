package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TilemodesTest {

    @Test
    fun `TilemodesGM matches tilemodes_png within tolerance`() {
        val gm = TilemodesGM(powerOfTwoSize = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tilemodes.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("TilemodesGM", comparison)
        if (comparison.similarity < 95.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TilemodesGM", comparison.similarity)
        assertTrue(accepted, "TilemodesGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 95.0,
            "TilemodesGM similarity ${"%.2f".format(comparison.similarity)}% < 95.0% floor",
        )
    }
}
