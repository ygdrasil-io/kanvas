package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ScaledTilemodesTest {

    @Test
    fun `ScaledTilemodesGM matches scaled_tilemodes_png within tolerance`() {
        val gm = ScaledTilemodesGM(powerOfTwoSize = true)
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image scaled_tilemodes.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("ScaledTilemodesGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ScaledTilemodesGM", comparison.similarity)
        assertTrue(accepted, "ScaledTilemodesGM regressed below tolerance")
        assertTrue(
            comparison.similarity >= 90.0,
            "ScaledTilemodesGM similarity ${"%.2f".format(comparison.similarity)}% < 90.0% floor",
        )
    }
}
