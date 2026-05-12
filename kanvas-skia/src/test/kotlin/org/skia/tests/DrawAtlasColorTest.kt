package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawAtlasColorTest {
    @Test
    fun `DrawAtlasColorGM matches draw-atlas-colors_png within tolerance`() {
        val gm = DrawAtlasColorGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image draw-atlas-colors.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawAtlasColorGM", comparison)
        if (comparison.similarity < 70.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawAtlasColorGM", comparison.similarity)
        assertTrue(accepted, "DrawAtlasColorGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 10.0,
            "DrawAtlasColorGM similarity ${"%.2f".format(comparison.similarity)}% < 10.0% floor",
        )
    }
}
