package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class DrawRegionModesTest {

    @Test
    fun `DrawRegionModesGM matches drawregionmodes_png within tolerance`() {
        val gm = DrawRegionModesGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image drawregionmodes.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("DrawRegionModesGM", comparison)
        if (comparison.similarity < 80.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("DrawRegionModesGM", comparison.similarity)
        assertTrue(accepted, "DrawRegionModesGM regressed below ratchet")
        // Heavy use of mask filter blur, image filter blur, gradients, dash effects —
        // tolerate broader pixel diff; ratchet will tighten over time.
        assertTrue(
            comparison.similarity >= 70.0,
            "DrawRegionModesGM similarity ${"%.2f".format(comparison.similarity)}% < 70.0%",
        )
    }
}
