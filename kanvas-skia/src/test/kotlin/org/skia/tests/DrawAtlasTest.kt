package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawAtlasTest {
    @Test
    fun `DrawAtlasGM matches draw-atlas_png within tolerance`() {
        val gm = DrawAtlasGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image draw-atlas.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawAtlasGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawAtlasGM", comparison.similarity)
        assertTrue(accepted, "DrawAtlasGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "DrawAtlasGM similarity ${"%.2f".format(comparison.similarity)}% < 30.0% floor",
        )
    }
}
