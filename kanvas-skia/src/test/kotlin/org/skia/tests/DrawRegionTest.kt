package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawRegionTest {

    @Test
    fun `DrawRegionGM matches drawregion_png within tolerance`() {
        val gm = DrawRegionGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drawregion.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawRegionGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawRegionGM", comparison.similarity)
        assertTrue(accepted, "DrawRegionGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 88.0,
            "DrawRegionGM similarity ${"%.2f".format(comparison.similarity)}% < 88.0% (t=1 floor)",
        )
    }
}
