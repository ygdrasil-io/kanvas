package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RepeatedBitmapTest {

    @Test
    fun `RepeatedBitmapGM matches repeated_bitmap_png within tolerance`() {
        val gm = RepeatedBitmapGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image repeated_bitmap.png")
        // 16 rotated cells over a 12-px checkerboard. Background + grey
        // rects + bitmap-shader-via-drawRect paths all rotate cleanly ;
        // residuals come from the shader sampling lined up against
        // upstream's `drawImage` nearest-neighbour rasterizer (within
        // a few ulps).
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RepeatedBitmapGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RepeatedBitmapGM", comparison.similarity)
        assertTrue(accepted, "RepeatedBitmapGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 99.5,
            "RepeatedBitmapGM similarity ${"%.2f".format(comparison.similarity)}% < 99.5% floor",
        )
    }
}
