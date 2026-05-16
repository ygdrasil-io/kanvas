package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class TiledScaledBitmapTest {

    @Test
    fun `TiledScaledBitmapGM matches tiledscaledbitmap_png within tolerance`() {
        val gm = TiledScaledBitmapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image tiledscaledbitmap.png")
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("TiledScaledBitmapGM", comparison)
        if (comparison.similarity < 99.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("TiledScaledBitmapGM", comparison.similarity)
        assertTrue(accepted, "TiledScaledBitmapGM regressed below ratchet")
    }
}
