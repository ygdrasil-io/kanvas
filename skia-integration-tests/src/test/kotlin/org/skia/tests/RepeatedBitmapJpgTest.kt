package org.skia.tests

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.SimilarityTracker
import org.skia.testing.TestReport
import org.skia.testing.TestUtils

class RepeatedBitmapJpgTest {

    @Test
    fun `RepeatedBitmapJpgGM matches repeated_bitmap_jpg_png within tolerance`() {
        val gm = RepeatedBitmapJpgGM()
        val rendered = TestUtils.runGmTest(gm)
        val reference = TestUtils.loadReferenceBitmap(gm.name())
        assertNotNull(reference, "Missing reference image repeated_bitmap_jpg.png")
        // 16 rotated cells over a 12-px checkerboard. JPEG source
        // (images/color_wheel.jpg) decoded and drawn via bitmap shader.
        // Residuals arise from the pure Kotlin JPEG path's chroma upsampling
        // and IDCT differing slightly from the historical ImageIO/libjpeg
        // baseline at rotated edges.
        val comparison = TestUtils.compareBitmapsDetailed(rendered, reference!!, tolerance = 1)
        TestReport.recordDetailed("RepeatedBitmapJpgGM", comparison)
        if (comparison.similarity < 90.0) {
            TestUtils.saveComparisonImage(rendered, reference, comparison, gm.name())
        }
        val accepted = SimilarityTracker.updateScore("RepeatedBitmapJpgGM", comparison.similarity)
        assertTrue(accepted, "RepeatedBitmapJpgGM regressed below ratchet")
        assertTrue(
            comparison.similarity >= 93.0,
            "RepeatedBitmapJpgGM similarity ${"%.2f".format(comparison.similarity)}% < 93.0% floor",
        )
    }
}
