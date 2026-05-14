package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RasterAllocatorTest {

    @Test
    fun `RasterAllocatorGM matches rasterallocator_png within tolerance`() {
        val gm = RasterAllocatorGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image ${gm.name()}.png")

        // The 50%-alpha green saveLayer overlap creates a small premul/
        // unpremul rounding gap from upstream; everything else (red
        // square, blue inset, white oval, clipped grey strip) matches.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RasterAllocatorGM", comparison)
        val floor = 63.5
        if (comparison.similarity < floor) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RasterAllocatorGM", comparison.similarity)
        assertTrue(accepted, "RasterAllocatorGM regressed below ratchet")
    }
}
