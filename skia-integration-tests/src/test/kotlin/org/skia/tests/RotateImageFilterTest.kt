package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RotateImageFilterTest {

    @Test
    fun `RotateImageFilterGM matches rotate_imagefilter_png within tolerance`() {
        val gm = RotateImageFilterGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image rotate_imagefilter.png")
        // Rotated rects with null / Blur / Blend(SrcOver) filters. Edge AA and
        // filter convolution accumulate rounding vs upstream; floor 30 % is safe.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 8)
        TestReport.recordDetailed("RotateImageFilterGM", comparison)
        if (comparison.similarity < 60.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RotateImageFilterGM", comparison.similarity)
        assertTrue(accepted, "RotateImageFilterGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 30.0,
            "RotateImageFilterGM similarity ${"%.2f".format(comparison.similarity)}% < 30% floor",
        )
    }
}
