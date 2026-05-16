package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class ClipRegionTest {

    @Test
    fun `ClipRegionGM matches clip_region_png within tolerance`() {
        val gm = ClipRegionGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image clip_region.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("ClipRegionGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("ClipRegionGM", comparison.similarity)
        assertTrue(accepted, "ClipRegionGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 50.0,
            "ClipRegionGM similarity ${"%.2f".format(comparison.similarity)}% < 50% floor",
        )
    }
}
