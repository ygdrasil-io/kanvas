package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class PictureShaderTileTest {
    @Test
    fun `PictureShaderTileGM matches pictureshadertile_png within tolerance`() {
        val gm = PictureShaderTileGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image pictureshadertile.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 4)
        TestReport.recordDetailed("PictureShaderTileGM", comparison)
        TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        val accepted = SimilarityTracker.updateScore("PictureShaderTileGM", comparison.similarity)
        assertTrue(accepted, "PictureShaderTileGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= EXPECTED_SIMILARITY,
            "PictureShaderTileGM similarity ${"%.2f".format(comparison.similarity)}% < $EXPECTED_SIMILARITY%",
        )
    }

    private companion object {
        const val EXPECTED_SIMILARITY: Double = 48.4
    }
}
